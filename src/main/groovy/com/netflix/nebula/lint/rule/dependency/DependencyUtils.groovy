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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.compile.AbstractCompile
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.jar.JarEntry
import java.util.jar.JarFile

class DependencyUtils {
    private static Logger logger = LoggerFactory.getLogger(DependencyUtils)

    static Set<String> classes(ResolvedDependency d) {
        def definedClasses = new HashSet<String>()

        d.moduleArtifacts
                .collect { it.file }
                .findAll { it.name.endsWith('.jar') }
                .findAll { !it.name.endsWith('-sources.jar') && !it.name.endsWith('-javadoc.jar') }
                .each {
            def jarFile = new JarFile(it)
            def allEntries = jarFile.entries()
            while (allEntries.hasMoreElements()) {
                def entry = allEntries.nextElement() as JarEntry
                if(entry.name.endsWith('.class'))
                    definedClasses += entry.name.replaceAll(/\.class$/, '')
            }
            jarFile.close()
        }

        return definedClasses
    }

    static Set<String> configurations(ResolvedDependency d, Project p) {
        return (p.configurations as Collection<Configuration>).findAll { it.resolvedConfiguration
                .firstLevelModuleDependencies.contains(d) }*.name
    }

    static ClassLoader compiledSourceClassLoader(Project p) {
        def jars = p.configurations*.resolvedConfiguration*.getFiles(Specs.SATISFIES_ALL).flatten()
                .findAll { it.name.endsWith('.jar') } as List<File>

        def classDirs = p.tasks.findAll { it instanceof AbstractCompile }
                .collect { it as AbstractCompile }
                .collect { it.destinationDir }
                .unique()
                .findAll { it.exists() }

        return new URLClassLoader((jars + classDirs).collect { it.toURI().toURL() } as URL[])
    }

    /**
     * @return a map of fully qualified class name to a ResolvedDependency set representing those jars in the
     * project's dependency configurations that contain the class
     */
    static Map<String, Set<ResolvedDependency>> resolvedDependenciesByClass(Project p) {
        def firstLevelDependencies = p.configurations*.resolvedConfiguration*.firstLevelModuleDependencies
                .flatten().unique() as Collection<ResolvedDependency>

        def classOwners = new HashMap<String, Set<ResolvedDependency>>().withDefault {[] as Set}
        def mvidsAlreadySeen = [] as Set

        def recurseFindClassOwnersSingle
        recurseFindClassOwnersSingle = { ResolvedDependency d ->
            for (clazz in classes(d)) {
                logger.debug('Class {} found in module {}', clazz, d.module.id)
                classOwners[clazz].add(d)
            }

            d.children.each {
                recurseFindClassOwnersSingle(it)
            }
        }

        def recurseFindClassOwners
        recurseFindClassOwners = { Collection<ResolvedDependency> ds ->
            if(ds.isEmpty()) return

            def notYetSeen = ds.findAll { d -> mvidsAlreadySeen.add(d.module.id) }

            notYetSeen.each { d ->
                for (clazz in classes(d)) {
                    logger.debug('Class {} found in module {}', clazz, d.module.id)
                    classOwners[clazz].add(d)
                }
            }

            recurseFindClassOwners(notYetSeen*.children.flatten())
        }

        recurseFindClassOwners(firstLevelDependencies)

        return classOwners
    }
}
