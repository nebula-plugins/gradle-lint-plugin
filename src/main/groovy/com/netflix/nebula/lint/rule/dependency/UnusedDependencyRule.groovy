package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
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

import static com.netflix.nebula.lint.rule.dependency.DependencyUtils.*

class UnusedDependencyRule extends GradleLintRule implements GradleModelAware {
    Collection<ResolvedDependency> firstLevelDependencies
    Set<ResolvedDependency> transitiveDependencies

    Collection<String> runtimeConfs = [
        'runtime',  // from java plugin
        'providedRuntime' // from war plugin
    ]

    Logger logger = LoggerFactory.getLogger(UnusedDependencyRule)
    Comparator<String> versionComparator = new DefaultVersionComparator().asStringComparator()

    Comparator<ResolvedDependency> dependencyComparator = new Comparator<ResolvedDependency>() {
        @Override
        int compare(ResolvedDependency d1, ResolvedDependency d2) {
            if(d1.moduleGroup != d2.moduleGroup)
                return d1?.moduleGroup?.compareTo(d2.moduleGroup) ?: d2.moduleGroup ? -1 : 1
            else if(d1.moduleName != d2.moduleName)
                return d1?.moduleName?.compareTo(d2.moduleName) ?: d2.moduleName ? -1 : 1
            else
                return versionComparator.compare(d1.moduleVersion, d2.moduleVersion)
        }
    }

    /**
     * @return a map of fully qualified class name to a ResolvedDependency set representing those jars in the
     * project's dependency configurations that contain the class
     */
    Map<String, Set<ResolvedDependency>> resolvedDependenciesByClass() {
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

    boolean unusedDependenciesCalculated = false
    Set<ResolvedDependency> firstOrderDependenciesToRemove = new HashSet()
    Set<ResolvedDependency> transitiveDependenciesToAddAsFirstOrder = new TreeSet(dependencyComparator)
    Map<ResolvedDependency, String> firstOrderDependenciesWhoseConfigurationNeedsToChange = [:]

    void calculateUnusedDependenciesIfNecessary() {
        if(unusedDependenciesCalculated)
            return

        firstLevelDependencies = project.configurations*.resolvedConfiguration*.firstLevelModuleDependencies
                .flatten().unique() as Collection<ResolvedDependency>

        transitiveDependencies = new HashSet()
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
        def resolvedDependenciesByClass = resolvedDependenciesByClass()

        DependencyReferences usedDependencies = project.convention.getPlugin(JavaPluginConvention)
                .sourceSets
                .inject(new DependencyReferences()) { DependencyReferences result, sourceSet ->
                    dependencyReferences(sourceSet, resolvedDependenciesByClass, result)
                }

        unusedDependencies.removeAll(usedDependencies.direct.keySet())

        for(d in firstLevelDependencies) {
            def confs = configurations(d, project)
            if(unusedDependencies.contains(d)) {
                logger.info("Unused dependency found $d.module.id")

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
                logger.info("Unsimplified required in: " + (usedDependencies.direct[d] + usedDependencies.indirect[d]))

                def confsRequiringDep = ConfigurationUtils.simplify(project, usedDependencies.direct[d] + usedDependencies.indirect[d])
                def actualConfs = ConfigurationUtils.simplify(project, confs)

                logger.info("Required in: $confsRequiringDep")
                logger.info("Actually in: $actualConfs")

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

//                if(!confs.contains('compile') && !confs.contains('testCompile') && !confs.contains('provided') &&
//                        !confs.contains('providedCompile')) {
//                    firstOrderDependenciesWhoseConfigurationNeedsToChange.put(d, 'compile')
//                }
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

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
//        if(project.rootProject == project) {
//            // TODO write a test for this
//            return // we won't touch dependencies in the root project
//        }

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

    class DependencyReferences {
        // maps of dependencies to the compile configuration of the source set that
        Map<ResolvedDependency, Set<String>> direct = [:].withDefault {[] as Set}
        Map<ResolvedDependency, Set<String>> indirect = [:].withDefault {[] as Set}
    }
}