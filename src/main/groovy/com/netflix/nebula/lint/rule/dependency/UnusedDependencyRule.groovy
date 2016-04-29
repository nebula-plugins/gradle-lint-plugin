/*
 * Copyright 2015-2016 Netflix, Inc.
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

import com.netflix.nebula.lint.rule.GradleDependency
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ResolvedDependency

class UnusedDependencyRule extends AbstractDependencyReportRule {
    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def matchesGradleDep = { ResolvedDependency d -> d.module.id.group == dep.group && d.module.id.name == dep.name }
        def match

        if ((match = report.firstOrderDependenciesWithNoClasses.find(matchesGradleDep))) {
            addBuildLintViolation("this dependency should be moved to the runtime configuration since it has no classes", call)
                    .replaceWith(call, "runtime '$match.module.id'")
        } else if (report.firstOrderDependenciesToRemove.find(matchesGradleDep)) {
            addBuildLintViolation('this dependency is unused and can be removed', call).delete(call)
        } else if ((match = report.firstOrderDependenciesWhoseConfigurationNeedsToChange.keySet().find(matchesGradleDep))) {
            def toConf = report.firstOrderDependenciesWhoseConfigurationNeedsToChange[match]
            addBuildLintViolation("this dependency should be moved to configuration $toConf", call)
                    .replaceWith(call, "$toConf '$match.module.id'")
        }
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if (call.methodAsString == 'dependencies') {
            // TODO match indentation of surroundings
            def indentation = ''.padLeft(call.columnNumber + 3)
            def transitiveSize = report.transitiveDependenciesToAddAsFirstOrder.size()

            if (transitiveSize == 1) {
                def d = report.transitiveDependenciesToAddAsFirstOrder.first()
                addBuildLintViolation('one or more classes in your transitive dependencies are required by your code directly')
                        .insertAfter(call, "${indentation}compile '${d.module.id}'")
            } else if (transitiveSize > 1) {
                addBuildLintViolation('one or more classes in your transitive dependencies are required by your code directly')
                        .insertAfter(call,
                        report.transitiveDependenciesToAddAsFirstOrder.toSorted(dependencyComparator).inject('') { deps, d ->
                            deps + "\n${indentation}compile '$d.module.id'"
                        }
                )
            }
        }
    }
}
