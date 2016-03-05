package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.tasks.compile.AbstractCompile
import org.objectweb.asm.ClassReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarEntry
import java.util.jar.JarFile

class UnusedDependencyRule extends GradleLintRule implements GradleModelAware {
    Collection<ResolvedDependency> firstLevelDependencies

    Logger logger = LoggerFactory.getLogger(UnusedDependencyRule)

    static Set<String> classSet(ResolvedDependency d) {
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

    Map<String, Set<ResolvedDependency>> classOwners() {
        def classOwners = new HashMap<String, Set<ResolvedDependency>>().withDefault {[] as Set}
        def mvidsAlreadySeen = [] as Set


        def recurseFindClassOwnersSingle
        recurseFindClassOwnersSingle = { ResolvedDependency d ->
            for (clazz in classSet(d)) {
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
                for (clazz in classSet(d)) {
                    logger.debug('Class {} found in module {}', clazz, d.module.id)
                    classOwners[clazz].add(d)
                }
            }

            recurseFindClassOwners(notYetSeen*.children.flatten())
        }

        recurseFindClassOwners(firstLevelDependencies)

        return classOwners
    }

    boolean unusedDependenciesCalculated = false
    Set<ResolvedDependency> firstOrderDependenciesToRemove = new HashSet()
    Set<ResolvedDependency> transitiveDependenciesToAddAsFirstOrder = new HashSet()
    Map<ResolvedDependency, String> firstOrderDependenciesWhoseConfigurationNeedsToChange = [:]

    Collection<String> configurations(ResolvedDependency d) {
        return project.configurations.findAll { it.resolvedConfiguration.firstLevelModuleDependencies.contains(d) }*.name
    }

    void calculateUnusedDependenciesIfNecessary() {
        if(unusedDependenciesCalculated)
            return

        firstLevelDependencies = project.configurations*.resolvedConfiguration*.firstLevelModuleDependencies
                .flatten().unique() as Collection<ResolvedDependency>

        // Unused first order dependencies
        Collection<ResolvedDependency> unusedDependencies = firstLevelDependencies.clone()

        // Used dependencies, both first order and transitive
        def classOwners = classOwners()
        Collection<ResolvedDependency> usedDependencies = project.tasks.findAll { it instanceof AbstractCompile }
                .collect { it as AbstractCompile }
                .collect { it.destinationDir }
                .unique()
                .findAll { it.exists() }
                .collect { dependencyReferences(it, classOwners) }
                .flatten()
                .unique { it.module.id } as Collection<ResolvedDependency>

        unusedDependencies.removeAll(usedDependencies)

        for(d in firstLevelDependencies) {
            def confs = configurations(d)
            if(unusedDependencies.contains(d)) {
                if(!confs.contains('compile') && !confs.contains('testCompile'))
                    continue

                firstOrderDependenciesToRemove.add(d)

                def inUse = transitivesInUse(d, usedDependencies)
                if(!inUse.isEmpty()) {
                    inUse.each { used ->
                        def matching = firstLevelDependencies.find {
                            it.module.id.group == used.moduleGroup && it.module.id.name == used.moduleName
                        }

                        if(!matching) {
                            transitiveDependenciesToAddAsFirstOrder.add(used)
                        }
                        else if(matching.configuration == 'runtime') {
                            firstOrderDependenciesWhoseConfigurationNeedsToChange.put(matching, 'compile')
                        }
                    }
                }
            }
            else {
                if(!confs.contains('compile') && !confs.contains('testCompile')) {
                    firstOrderDependenciesWhoseConfigurationNeedsToChange.put(d, 'compile')
                }
            }
        }

        // only keep the highest version of each transitive module that we need to add as a first order dependency
        def versionComparator = new DefaultVersionComparator().asStringComparator()
        transitiveDependenciesToAddAsFirstOrder = transitiveDependenciesToAddAsFirstOrder
                .groupBy { "$it.moduleGroup:$it.moduleName".toString() }
                .values()
                .collect {
                    it.sort { d1, d2 -> versionComparator.compare(d2.moduleVersion, d1.moduleVersion) }.first()
                }
                .toSet()

        unusedDependenciesCalculated = true
    }

    private Set<ResolvedDependency> transitivesInUse(ResolvedDependency d, Collection<ResolvedDependency> usedDependencies) {
        def childrenInUse = d.children.collect { transitivesInUse(it, usedDependencies) }.flatten() as Collection<ResolvedDependency>
        if(usedDependencies.contains(d)) {
            return childrenInUse.plus(d).toSet()
        }
        else
            return childrenInUse
    }

    Set<ResolvedDependency> dependencyReferences(File classesDir, Map<String, Set<ResolvedDependency>> classOwners) {
        def references = new HashSet<ResolvedDependency>()

        logger.debug('Looking for classes to examine in {}', classesDir)

        Files.walkFileTree(classesDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if(file.toFile().name.endsWith('.class')) {
                    logger.debug('Examining {} for first-order dependency references', file.toFile().path)
                    def fin = file.newInputStream()
                    def visitor = new DependencyClassVisitor(classOwners, logger)
                    new ClassReader(fin).accept(visitor, ClassReader.SKIP_DEBUG)
                    references.addAll(visitor.references)
                }
                return FileVisitResult.CONTINUE
            }
        })

        return references
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        calculateUnusedDependenciesIfNecessary()

        def matchesGradleDep = { ResolvedDependency d -> d.module.id.group == dep.group && d.module.id.name == dep.name }
        def match

        if(firstOrderDependenciesToRemove.find(matchesGradleDep)) {
            addViolationToDelete(call, 'this dependency is unused and can be removed')
        }
        else if((match = firstOrderDependenciesWhoseConfigurationNeedsToChange.keySet().find(matchesGradleDep))) {
            def toConf = firstOrderDependenciesWhoseConfigurationNeedsToChange[match]
            addViolationWithReplacement(call, 'this dependency is required at compile time',
                    "$toConf '$match.module.id")
        }
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(call.methodAsString == 'dependencies') {
            // TODO match indentation of surroundings
            def indentation = ''.padLeft(call.columnNumber + 3)
            def transitiveSize = transitiveDependenciesToAddAsFirstOrder.size()

            if(transitiveSize == 1) {
                def d = transitiveDependenciesToAddAsFirstOrder.first()
                addViolationInsert(call, 'one or more classes in this transitive dependency are required by your code directly',
                        "\n${indentation}compile '$d.module.id'")
            }
            else if(transitiveSize > 1) {
                addViolationInsert(call, 'one or more classes in these transitive dependencies are required by your code directly',
                        transitiveDependenciesToAddAsFirstOrder.inject('') {
                            deps, d -> deps + "\n${indentation}compile '$d.module.id'"
                        })
            }
        }
    }
}