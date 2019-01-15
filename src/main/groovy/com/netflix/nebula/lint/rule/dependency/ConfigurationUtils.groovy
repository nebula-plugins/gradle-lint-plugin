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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.UnknownConfigurationException

class ConfigurationUtils {
    static Set<String> simplify(Project p, String... configurations) {
        simplify(p, configurations.toList())
    }

    static Set<String> simplify(Project p, Collection<String> configurations) {
        def hasCompile = (p.configurations as Collection<Configuration>).find { it.name == 'compile' }

        def simplified = configurations.collect {
            try {
                def hierarchyAtOrBelowCompile
                hierarchyAtOrBelowCompile = { Configuration c ->
                    def ext = c.extendsFrom
                    if(c.name == 'compile' || ext.isEmpty()) return [c]

                    def confs = c.extendsFrom.collect { hierarchyAtOrBelowCompile(it) }.flatten() as Collection<Configuration>
                    return confs + [c]
                }

                hierarchyAtOrBelowCompile(p.configurations.getByName(it)).find { configurations.contains(it.name) }
            } catch(UnknownConfigurationException ignored) {
                null
            }
        }

        simplified.findAll { Configuration c ->
            // supposing that simplified contains [compile, provided], we want to simplify to [compile] and not [provided] even
            // though compile extends from provided
            def isNotBelowCompile = (!hasCompile ||
                    p.configurations.compile == c ||
                    (!p.configurations.compile.hierarchy.contains(c) ||
                            !simplified.contains(p.configurations.compile)))

            c && isNotBelowCompile
        }.collect { it.name } as Set
    }
}
