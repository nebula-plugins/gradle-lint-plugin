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

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.Configuration
import org.gradle.api.specs.Specs

class UnusedExcludeByConfigurationRule extends GradleLintRule implements GradleModelAware {
    String description = 'excludes that have no effect on the classpath should be removed for clarity'

    @Override
    void visitConfigurationExclude(MethodCallExpression call, String conf, GradleDependency exclude) {
        // Since Gradle does not expose any information about which excludes were effective, we will create a new configuration
        // lintExcludeConf, adding all first order dependencies from the conf that the exclusion applies to and resolve it.
        Configuration lintExcludeConf = project.configurations.create("lintExcludes")
        project.configurations.findAll { (conf == 'all' || conf == it.name) && it != lintExcludeConf }*.allDependencies.flatten().each { d ->
            project.dependencies.add(lintExcludeConf.name, "$d.group:$d.name:$d.version")
        }

        // If we find a dependency in the transitive closure of this special conf, then we can infer that the exclusion is
        // doing something. Note that all*.exclude style exclusions are applied to all of the configurations at the time
        // of project evaluation, but not lintExcludeConf.
        def excludeIsInTransitiveClosure = false
        def deps = lintExcludeConf.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)
        while(!deps.isEmpty() && !excludeIsInTransitiveClosure) {
            deps = deps.collect { d ->
                if((!exclude.group || d.moduleGroup == exclude.group) && (!exclude.name || d.moduleName == exclude.name)) {
                    excludeIsInTransitiveClosure = true
                }
                d.children
            }
            .flatten()
        }

        project.configurations.remove(lintExcludeConf)

        if(!excludeIsInTransitiveClosure) {
            addBuildLintViolation('the exclude dependency is not in your dependency graph, so has no effect', call)
                .delete(call)
        }
    }
}
