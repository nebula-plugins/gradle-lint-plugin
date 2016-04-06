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
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator

class UnusedDependencyRule extends GradleLintRule implements GradleModelAware {
    UnusedDependencyReport report

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if(report == null) {
            report = UnusedDependencyReport.forProject(project)
        }

        def matchesGradleDep = { ResolvedDependency d -> d.module.id.group == dep.group && d.module.id.name == dep.name }
        def match

        if(report.firstOrderDependenciesToRemove.find(matchesGradleDep)) {
            addViolationToDelete(call, 'this dependency is unused and can be removed')
        }
        else if((match = report.firstOrderDependenciesWhoseConfigurationNeedsToChange.keySet().find(matchesGradleDep))) {
            def toConf = report.firstOrderDependenciesWhoseConfigurationNeedsToChange[match]
            addViolationWithReplacement(call, "this dependency should be moved to configuration $toConf",
                    "$toConf '$match.module.id'")
        }
    }

    private Comparator<ResolvedDependency> dependencyComparator = new Comparator<ResolvedDependency>() {
        @Override
        int compare(ResolvedDependency d1, ResolvedDependency d2) {
            if(d1.moduleGroup != d2.moduleGroup)
                return d1?.moduleGroup?.compareTo(d2.moduleGroup) ?: d2.moduleGroup ? -1 : 1
            else if(d1.moduleName != d2.moduleName)
                return d1?.moduleName?.compareTo(d2.moduleName) ?: d2.moduleName ? -1 : 1
            else
                return new DefaultVersionComparator().asStringComparator().compare(d1.moduleVersion, d2.moduleVersion)
        }
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(report == null) {
            report = UnusedDependencyReport.forProject(project)
        }

        if(call.methodAsString == 'dependencies') {
            // TODO match indentation of surroundings
            def indentation = ''.padLeft(call.columnNumber + 3)
            def transitiveSize = report.transitiveDependenciesToAddAsFirstOrder.size()

            if(transitiveSize == 1) {
                def d = report.transitiveDependenciesToAddAsFirstOrder.first()
                addViolationInsert(null, 'one or more classes in your transitive dependencies are required by your code directly',
                        "\n${indentation}compile '$d.module.id'", call)
            }
            else if(transitiveSize > 1) {
                addViolationInsert(null, 'one or more classes in your transitive dependencies are required by your code directly',
                        report.transitiveDependenciesToAddAsFirstOrder.toSorted(dependencyComparator).inject('') { deps, d ->
                            deps + "\n${indentation}compile '$d.module.id'"
                        }, call)
            }
        }
    }
}