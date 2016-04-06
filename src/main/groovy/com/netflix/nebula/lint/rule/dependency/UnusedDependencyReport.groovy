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
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.objectweb.asm.ClassReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

import static com.netflix.nebula.lint.rule.dependency.DependencyUtils.compiledSourceClassLoader
import static com.netflix.nebula.lint.rule.dependency.DependencyUtils.configurations
import static com.netflix.nebula.lint.rule.dependency.DependencyUtils.resolvedDependenciesByClass

class UnusedDependencyReport {
    Set<ResolvedDependency> firstOrderDependenciesToRemove = new HashSet()
    Set<ResolvedDependency> transitiveDependenciesToAddAsFirstOrder = new HashSet()
    Map<ResolvedDependency, String> firstOrderDependenciesWhoseConfigurationNeedsToChange = [:]

    private Collection<String> runtimeConfs = [
            'runtime',  // from java plugin
            'providedRuntime' // from war plugin
    ]

    private static Logger logger = LoggerFactory.getLogger(UnusedDependencyReport)
    private Comparator<String> versionComparator = new DefaultVersionComparator().asStringComparator()
    private Project project

    private static Map<Project, UnusedDependencyReport> reportsByProject = [:]

    /**
     * @param project
     * @return the unused dependency report, calculated once per project per lint session, regardless of how many
     * rules use the report
     */
    static synchronized UnusedDependencyReport forProject(Project project) {
        def report = reportsByProject[project]
        if(report) return report

        report = new UnusedDependencyReport(project)
        reportsByProject[project] = report
        return report
    }

    private UnusedDependencyReport(Project project) {
        this.project = project

        def firstLevelDependencies = project.configurations*.resolvedConfiguration*.firstLevelModuleDependencies
                .flatten().unique() as Collection<ResolvedDependency>

        def transitiveDependencies = new HashSet()
        def recurseTransitives
        recurseTransitives = { Collection<ResolvedDependency> ds ->
            ds.each { d ->
                transitiveDependencies.add(d)
                recurseTransitives(d.children)
            }
        }
        recurseTransitives(firstLevelDependencies*.children.flatten() as Collection<ResolvedDependency>)

        // Unused first order dependencies
        Collection<ResolvedDependency> unusedDependencies = firstLevelDependencies.clone() as Collection<ResolvedDependency>

        // Used dependencies, both first order and transitive
        def resolvedDependenciesByClass = resolvedDependenciesByClass(project)

        DependencyReferences usedDependencies
        try {
            usedDependencies = project.convention.getPlugin(JavaPluginConvention)
                    .sourceSets
                    .inject(new DependencyReferences()) { DependencyReferences result, sourceSet ->
                dependencyReferences(sourceSet, resolvedDependenciesByClass, result)
            }
        } catch(IllegalStateException e) {
            return // no Java plugin convention, so nothing further we can do...
        }

        unusedDependencies.removeAll(usedDependencies.direct.keySet())

        for(d in firstLevelDependencies) {
            def confs = configurations(d, project)
            if(unusedDependencies.contains(d)) {
                if(!confs.contains('compile') && !confs.contains('testCompile'))
                    continue

                if(unusedDependencies.contains('provided')) {
                    // these are treated as both compile and runtime type dependencies; the nebula extra-configurations
                    // plugin doesn't provide enough detail to differentiate between the two
                    continue // we have to assume that this dependency is a runtime dependency
                }

                if(!usedDependencies.indirect[d].isEmpty() && !transitiveDependencies.contains(d)) {
                    // this dependency is indirectly used -- if we remove it as a first order dependency,
                    // it will break compilation
                    continue
                }

                firstOrderDependenciesToRemove.add(d)

                // determine if a transitive is directly referenced and needs to be promoted to a first-order dependency
                def inUse = transitivesInUse(d, usedDependencies.direct.keySet())
                if(!inUse.isEmpty()) {
                    inUse.each { used ->
                        def matching = firstLevelDependencies.find {
                            it.module.id.group == used.moduleGroup && it.module.id.name == used.moduleName
                        }

                        if(!matching) {
                            transitiveDependenciesToAddAsFirstOrder.add(used)
                        }
                        else if(runtimeConfs.contains(matching.configuration)) {
                            firstOrderDependenciesWhoseConfigurationNeedsToChange.put(matching, 'compile')
                        }
                    }
                }
            }
            // this is a dependency which is used at compile time for one or more source sets
            else {
                def confsRequiringDep = ConfigurationUtils.simplify(project, usedDependencies.direct[d] + usedDependencies.indirect[d])
                def actualConfs = ConfigurationUtils.simplify(project, confs)

                if(confsRequiringDep.size() == 1) { // except in the rare case of disjoint configurations, this will be true
                    if(!actualConfs.contains(confsRequiringDep[0])) {
                        firstOrderDependenciesWhoseConfigurationNeedsToChange.put(d, confsRequiringDep[0])
                    }
                    else {
                        // this dependency is defined in the correct configuration, nothing needs to change
                    }
                }
                else {
                    // TODO this is a complicated case...

                    // if the opposite difference (confsRequiringDeps - actualConfs) is non-empty, the code should
                    // not compile, so we don't need to do anything in this lint rule
                }
            }
        }

        // only keep the highest version of each transitive module that we need to add as a first order dependency
        transitiveDependenciesToAddAsFirstOrder = transitiveDependenciesToAddAsFirstOrder
                .groupBy { "$it.moduleGroup:$it.moduleName".toString() }
                .values()
                .collect {
            it.sort { d1, d2 -> versionComparator.compare(d2.moduleVersion, d1.moduleVersion) }.first()
        }
        .toSet()
    }

    private Set<ResolvedDependency> transitivesInUse(ResolvedDependency d, Collection<ResolvedDependency> usedDependencies) {
        def childrenInUse = d.children.collect { transitivesInUse(it, usedDependencies) }.flatten() as Collection<ResolvedDependency>
        if(usedDependencies.contains(d)) {
            return childrenInUse.plus(d).toSet()
        }
        else
            return childrenInUse
    }

    DependencyReferences dependencyReferences(SourceSet sourceSet,
                                              Map<String, Set<ResolvedDependency>> resolvedDependenciesByClass,
                                              DependencyReferences references) {
        logger.debug('Looking for classes to examine in {}', sourceSet.output.classesDir)

        if(!sourceSet.output.classesDir.exists()) return references

        Files.walkFileTree(sourceSet.output.classesDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if(file.toFile().name.endsWith('.class')) {
                    logger.debug('Examining {} for first-order dependency directReferences', file.toFile().path)
                    def fin = file.newInputStream()

                    def visitor = new DependencyClassVisitor(resolvedDependenciesByClass, compiledSourceClassLoader(project))
                    new ClassReader(fin).accept(visitor, ClassReader.SKIP_DEBUG)

                    visitor.directReferences.each { d -> references.direct[d].add(sourceSet.compileConfigurationName) }
                    visitor.indirectReferences.each { d -> references.indirect[d].add(sourceSet.compileConfigurationName) }
                }
                return FileVisitResult.CONTINUE
            }
        })

        return references
    }

    class DependencyReferences {
        // maps of dependencies to the compile configuration of the source set that
        Map<ResolvedDependency, Set<String>> direct = [:].withDefault {[] as Set}
        Map<ResolvedDependency, Set<String>> indirect = [:].withDefault {[] as Set}
    }
}
