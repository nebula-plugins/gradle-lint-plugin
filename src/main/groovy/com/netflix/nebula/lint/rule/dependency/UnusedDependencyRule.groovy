package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
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
    def unusedDependencies

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

    Map<String, Set<ModuleVersionIdentifier>> firstOrderDependencyClassOwners() {
        def classOwners = new HashMap<String, Set<ModuleVersionIdentifier>>().withDefault {[] as Set}
        def mvidsAlreadySeen = [] as Set

        project.configurations*.resolvedConfiguration*.firstLevelModuleDependencies*.each { ResolvedDependency d ->
            if (mvidsAlreadySeen.add(d.module.id)) {
                for(clazz in classSet(d))
                    classOwners[clazz].add(d.module.id)
            }
        }

        return classOwners
    }

    void calculateUnusedDependenciesIfNecessary() {
        if(!unusedDependencies) {
            def classOwners = firstOrderDependencyClassOwners()

            unusedDependencies = project
                    .configurations*.resolvedConfiguration*.firstLevelModuleDependencies*.collect { it.module.id }
                    .flatten()
                    .unique()
                    .collect { ModuleVersionIdentifier mvid -> "$mvid.group:$mvid.name".toString() }

            def referencedDependencies = project.tasks.findAll { it instanceof AbstractCompile }
                    .collect {
                        logger.debug('Looking for output dir for task {}', it.name)
                        it as AbstractCompile
                    }
                    .collect { it.destinationDir }
                    .unique()
                    .findAll { it.exists() }
                    .collect { dependencyReferences(it, classOwners) }
                    .flatten()
                    .unique()
                    .collect { ModuleVersionIdentifier mvid -> "$mvid.group:$mvid.name".toString() }

            unusedDependencies.removeAll(referencedDependencies)
        }
    }

    Set<ModuleVersionIdentifier> dependencyReferences(File classesDir, Map<String, Set<ModuleVersionIdentifier>> classOwners) {
        def references = new HashSet<ModuleVersionIdentifier>()

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
        if(unusedDependencies.contains("$dep.group:$dep.name".toString()))
            addViolationToDelete(call, 'this dependency is unused and can be removed')
    }
}