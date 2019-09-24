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
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.Configuration
import org.gradle.api.specs.Specs

class UnusedDependencyExcludeRule extends GradleLintRule implements GradleModelAware {
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
                            .delete(call)
                }
            }
        }
    }

    private boolean isExcludeUnnecessary(String group, String name) {
        // Since Gradle does not expose any information about which excludes were effective, we will create a new configuration
        // lintExcludeConf, add the dependency and resolve it.
        Configuration lintExcludeConf = project.configurations.create("lintExcludes")
        project.dependencies.add(lintExcludeConf.name, "$dependency.group:$dependency.name:$dependency.version")

        // If we find a dependency in the transitive closure of this special conf, then we can infer that the exclusion is
        // doing something. Note that all*.exclude style exclusions are applied to all of the configurations at the time
        // of project evaluation, but not lintExcludeConf.
        def excludeIsInTransitiveClosure = false
        def deps = lintExcludeConf.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)
        while(!deps.isEmpty() && !excludeIsInTransitiveClosure) {
            deps = deps.collect { d ->
                if((!group || d.moduleGroup == group) && (!name || d.moduleName == name)) {
                    excludeIsInTransitiveClosure = true
                }
                d.children
            }
            .flatten()
        }

        project.configurations.remove(lintExcludeConf)

        !excludeIsInTransitiveClosure
    }
}
