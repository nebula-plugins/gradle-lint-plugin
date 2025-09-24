/*
 * Copyright 2015-2019 Netflix, Inc.
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

package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleAstUtil
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.ModelAwareGradleLintRule
import org.codehaus.groovy.ast.expr.MethodCallExpression

class UnusedDependencyExcludeRule extends ModelAwareGradleLintRule {
    String description = 'excludes that have no effect on the classpath should be removed for clarity'

    GradleDependency dependency
    MethodCallExpression dependencyCall

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        dependency = dep
        dependencyCall = call
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(callStack.contains(dependencyCall)) {
            // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/ModuleDependency.html#exclude(java.util.Map)
            if (call.methodAsString == 'exclude') {
                def entries = GradleAstUtil.collectEntryExpressions(call)
                if (isExcludeUnnecessary(entries.group, entries.module)) {
                    addBuildLintViolation("the excluded dependency is not a transitive of $dependency.group:$dependency.name:$dependency.version, so has no effect", call)
                            .documentationUri("https://github.com/nebula-plugins/gradle-lint-plugin/wiki/Unused-Exclude-Rule")
                            .delete(call)
                }
            }
        }
    }

    private boolean isExcludeUnnecessary(String group, String name) {
        // Use detached configurations instead of project configurations to avoid threading issues
        // in Gradle 9.x which requires exclusive locks for configuration resolution
        def detachedConf = project.configurations.detachedConfiguration(
            project.dependencies.create("$dependency.group:$dependency.name:$dependency.version")
        )

        // This is thread-safe and doesn't require exclusive locks
        def resolutionResult = detachedConf.incoming.resolutionResult
        
        def excludeIsInTransitiveClosure = false
        
        def allComponents = resolutionResult.allComponents
        
        for (component in allComponents) {
            def moduleVersion = component.moduleVersion
            if (moduleVersion && 
                (!group || moduleVersion.group == group) && 
                (!name || moduleVersion.name == name)) {
                excludeIsInTransitiveClosure = true
                break
            }
        }
        
        return !excludeIsInTransitiveClosure
    }
}
