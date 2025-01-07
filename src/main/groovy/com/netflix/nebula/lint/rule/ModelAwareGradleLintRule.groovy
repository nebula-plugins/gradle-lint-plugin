/*
 * Copyright 2015-2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.DefaultDomainObjectCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.configuration.ImportsReader

import javax.annotation.Nullable

abstract class ModelAwareGradleLintRule extends GradleLintRule {
    Project project
    Map<String, List<String>> projectDefaultImports = null

    TypeInformation receiver(MethodCallExpression call) {
        List<Expression> fullCallStack = typedDslStack(callStack + call)
        List<TypeInformation> typedStack = []
        for (Expression currentMethod in fullCallStack) {
            if (typedStack.empty) {
                typedStack.add(new TypeInformation(project))
            }
            while (!typedStack.empty) {
                def current = typedStack.last()
                def candidate = findDirectCandidate(current, currentMethod)
                if (candidate != null) {
                    typedStack.add(candidate)
                    break
                }
                typedStack.removeLast()
            }
        }
        if (typedStack.size() >= 2) { //there should be the method return type and the receiver at least
            return typedStack[-2]
        } else {
            return null
        }
    }

    private findDirectCandidate(TypeInformation current, Expression currentExpression) {
        String methodName
        switch (currentExpression) {
            case MethodCallExpression:
                methodName = currentExpression.methodAsString
                break
            case PropertyExpression:
                methodName = currentExpression.propertyAsString
                break
            case VariableExpression:
                methodName = currentExpression.text
                break
            case ConstantExpression:
                methodName = currentExpression.text
                break
            default:
                return null
        }
        def getter = current.clazz.getMethods().find { it.name == "get${methodName.capitalize()}" }
        if (getter != null) {
            if (current.object != null) {
                try {
                    return new TypeInformation(getter.invoke(current.object))
                } catch (ignored) {
                    // ignore and fallback to the return type
                }
            }
            return new TypeInformation(null, getter.returnType)
        }

        // there is no public API for DomainObjectCollection.type
        if (current.object != null && DefaultDomainObjectCollection.class.isAssignableFrom(current.clazz)) {
            def collectionItemType = ((DefaultDomainObjectCollection) current.object).type

            if (methodName == "withType" && currentExpression instanceof MethodCallExpression && currentExpression.arguments.size() >= 1) {
                def className = currentExpression.arguments[0]
                def candidate = findSuitableClass(className.text, collectionItemType)
                if (candidate != null) {
                    collectionItemType = candidate
                }
            }

            if ((methodName == "create" || methodName == "register") && currentExpression instanceof MethodCallExpression && currentExpression.arguments.size() >= 2 && currentExpression.arguments[1] !instanceof ClosureExpression) {
                def className = currentExpression.arguments[1]
                def candidate = findSuitableClass(className.text, collectionItemType)
                if (candidate != null) {
                    collectionItemType = candidate
                }
            }

            def transformationOrFactoryMethod = current.clazz.getMethods().find { it.name == methodName && it.parameterTypes[-1] == Action.class }
            if (transformationOrFactoryMethod != null) {
                if (collectionItemType.isAssignableFrom(transformationOrFactoryMethod.returnType)) {
                    return new TypeInformation(null, transformationOrFactoryMethod.returnType)
                } else {
                    // assume that all actions are done on the collection type
                    return new TypeInformation(null, collectionItemType)
                }
            }
        }

        // note that we can't use tasks.findByName because it may lead to unwanted side effects because of potential task creation
        if (Project.class.isAssignableFrom(current.clazz) && methodName == "task" && currentExpression instanceof MethodCallExpression) {
            def taskType = extractTaskType(currentExpression)
            if (taskType != null) {
                return new TypeInformation(null, taskType)
            }
            return new TypeInformation(null, Task.class)
        }

        def factoryMethod = current.clazz.getMethods().find { it.name == methodName && it.parameterTypes[-1] == Action.class }
        if (factoryMethod != null) {
            // assume that this is a factory method that returns the created type
            return new TypeInformation(null, factoryMethod.returnType)
        }

        if (current.object != null && current.object instanceof ExtensionAware) {
            def extension = current.object.extensions.findByName(methodName)
            if (extension != null) {
                return new TypeInformation(extension)
            }
        }
        return null;
    }

    private List<Class> findClassInScope(String name) {
        if (this.projectDefaultImports == null) {
            this.projectDefaultImports = project.services.get(ImportsReader.class).getSimpleNameToFullClassNamesMapping()
        }
        return this.projectDefaultImports.get(name);
    }

    @Nullable
    private Class findSuitableClass(String className, Class parentClass) {
        def candidates = (findClassInScope(className) ?: []) + [className]
        for (String candidate in candidates) {
            try {
                def candidateClass = Class.forName(candidate)
                if (parentClass.isAssignableFrom(candidateClass)) {
                    return candidateClass
                }
            } catch (ignored) {
                // ignore and try the next candidate
            }
        }
        return null
    }

    @Nullable
    private Class extractTaskType(MethodCallExpression currentExpression) {
        for (Expression arg in currentExpression.arguments) {
            if (arg instanceof VariableExpression || arg instanceof ConstantExpression) {
                def candidate = findSuitableClass(arg.text, Task.class)
                if (candidate != null) {
                    return candidate
                }
            } else if (arg instanceof MapExpression) {
                def type = arg
                        .mapEntryExpressions
                        .find { it.keyExpression.text == "type" }
                        ?.valueExpression?.text
                if (type != null) {
                    def candidate = findSuitableClass(type, Task.class)
                    if (candidate != null) {
                        return candidate
                    }
                }
            }
        }
        return null
    }
}

class TypeInformation {
    @Nullable
    Class clazz
    @Nullable
    Object object

    TypeInformation(Object object) {
        this.object = object
        this.clazz = object.class
    }

    TypeInformation(Object object, Class clazz) {
        this.object = object
        this.clazz = clazz
    }
}