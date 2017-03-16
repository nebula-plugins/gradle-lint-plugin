package com.netflix.nebula.lint.rule.dependency

import groovy.transform.Memoized
import groovyx.gpars.GParsPool
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
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
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipException

class DependencyService {
    private static final String EXTENSION_NAME = "gradleLintDependencyService"

    static synchronized DependencyService forProject(Project project) {
        def extension = project.extensions.findByType(DependencyServiceExtension)
        if (!extension) {
            extension = project.extensions.create(EXTENSION_NAME, DependencyServiceExtension)
            extension.dependencyService = new DependencyService(project)
        }
        return extension.dependencyService
    }

    static class DependencyServiceExtension {
        DependencyService dependencyService
    }

    private final Logger logger = LoggerFactory.getLogger(DependencyService)

    Project project

    private Comparator<String> versionComparator = new DefaultVersionComparator().asStringComparator()

    private DependencyService(Project project) {
        this.project = project
    }

    Map<String, Collection<ResolvedArtifact>> artifactsByClass(String conf) {
        artifactsByClass(project.configurations.getByName(conf))
    }

    /**
     * @return a map of fully qualified class names to the resolved artifacts that contain them
     */
    @Memoized
    Map<String, Collection<ResolvedArtifact>> artifactsByClass(Configuration conf) {
        // we go up the configuration hierarchy here, because we want to identify dependencies that were defined in
        // higher configurations but belong here (e.g. defined in runtime, but in use in compile)
        List<Configuration> terminalConfs = []
        def extendingConfs
        extendingConfs = { Configuration c ->
            def subconfs = resolvableConfigurations().findAll { it.extendsFrom.any { it == c } }
            for (Configuration subconf in subconfs)
                extendingConfs(subconf)
            if (subconfs.isEmpty()) {
                terminalConfs.add(c)
            }
        }
        extendingConfs(conf)

        def artifactsByClass = [:].withDefault { [] }
        def artifactsToScan = terminalConfs*.resolvedConfiguration*.resolvedArtifacts.flatten().toSet() as Set<ResolvedArtifact>

        GParsPool.withPool {
            artifactsToScan
                    .findAll {
                it.file.name.endsWith('.jar') &&
                        !it.file.name.endsWith('-sources.jar') &&
                        !it.file.name.endsWith('-javadoc.jar')
            }
            .eachParallel { ResolvedArtifact artifact ->
                jarContents(artifact.file).classes.each { clazz ->
                    synchronized (artifactsByClass) {
                        artifactsByClass[clazz] += artifact
                    }
                }
            }
        }

        return artifactsByClass
    }

    /**
     * @param id
     * @return the contents of an artifact matching this module version, or <code>null</code> if no such
     * artifact was found on the resolved classpath
     */
    JarContents jarContents(ModuleIdentifier id) {
        def configurations = resolvableConfigurations()
        for (Configuration conf in configurations) {
            def artifact = conf.resolvedConfiguration.resolvedArtifacts.find { it.moduleVersion.id.module == id }
            if (artifact)
                return jarContents(artifact.file)
        }
        return null
    }

    Set<Configuration> resolvableConfigurations() {
        return project.configurations.findAll { isResolvable(it) }
    }

    @SuppressWarnings("GrMethodMayBeStatic") // Static memoization will leak
    @Memoized
    JarContents jarContents(File file) {
        if (!file.exists())
            return new JarContents(entryNames: Collections.emptyList())

        def entryNames = []

        try {
            new JarFile(file).withCloseable { jarFile ->
                def allEntries = jarFile.entries()
                while (allEntries.hasMoreElements()) {
                    entryNames += (allEntries.nextElement() as JarEntry).name
                }
            }
        } catch (ZipException ignored) {
            // this is not a valid jar file
        }

        return new JarContents(entryNames: entryNames)
    }

    final static Comparator<ModuleVersionIdentifier> DEPENDENCY_COMPARATOR = new Comparator<ModuleVersionIdentifier>() {
        @Override
        int compare(ModuleVersionIdentifier m1, ModuleVersionIdentifier m2) {
            if (m1.group != m2.group)
                return m1.group.compareTo(m2.group)
            else if (m1.name != m2.name)
                return m1.name.compareTo(m2.name)
            else
                return new DefaultVersionComparator().asStringComparator().compare(m1.version, m2.version)
        }
    }

    @Memoized
    private DependencyReferences classReferences(String confName) {
        def conf = project.configurations.getByName(confName)
        def classpath = sourceSetClasspath(confName)
        def output = sourceSetOutput(confName)

        if (!output || !output.exists())
            return null

        def artifactsByClass = artifactsByClass(conf)
        def references = new DependencyReferences()

        def compiledSourceClassLoader = new URLClassLoader((classpath + output)
                .collect { it.toURI().toURL() } as URL[], null as ClassLoader)

        Files.walkFileTree(output.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toFile().name.endsWith('.class')) {
                    try {
                        def visitor = new DependencyClassVisitor(artifactsByClass, compiledSourceClassLoader)
                        file.newInputStream().withCloseable { inputStream ->
                            new ClassReader(inputStream).accept(visitor, ClassReader.SKIP_DEBUG)

                            references.direct.addAll(visitor.directReferences)
                            references.indirect.addAll(visitor.indirectReferences)
                        }
                    } catch (Throwable t) {
                        // see https://github.com/nebula-plugins/gradle-lint-plugin/issues/88
                        // type annotations can cause ArrayIndexOutOfBounds in ASM:
                        // http://forge.ow2.org/tracker/index.php?func=detail&aid=317615&group_id=23&atid=100023
                        logger.debug("unable to read class ${file.toFile().name}", t)
                    }
                }
                return FileVisitResult.CONTINUE
            }
        })

        return references
    }

    @Memoized
    Set<ModuleVersionIdentifier> undeclaredDependencies(String confName) {
        def references = classReferences(confName)
        if (!references)
            return Collections.emptySet()

        def conf = project.configurations.getByName(confName)
        def required = (references.direct + references.indirect).collect { it.moduleVersion.id }
                .groupBy { it.module }
                .values()
                .collect {
            it.sort { d1, d2 -> versionComparator.compare(d2.version, d1.version) }.first()
        }
        .toSet()

        def declared = conf.resolvedConfiguration.firstLevelModuleDependencies.collect { it.module.id }

        return (required - declared).toSorted(DEPENDENCY_COMPARATOR).toSet()
    }

    /**
     * A configuration that has any subconfiguration that is not a transitive superconfiguration of some
     * source set is a runtime configuration.
     *
     * Ex: provided from nebula is a superconfiguration of compile and runtime, so even though provided is a
     * compile configuration w.r.t. compile, it is a runtime configuration w.r.t. runtime
     *
     * @param conf
     * @return <code>true</code> if this is a runtime configuration, even if it is also a compile configuration
     */
    @Memoized
    boolean isRuntime(String confName) {
        if (confName == 'compileOnly')
            return false

        if (confName == 'providedCompile' && project.plugins.hasPlugin('war')) {
            // Gradle should not have made providedRuntime extend from providedCompile, since the runtime conf would already
            // have inherited providedCompile dependencies via the compile -> providedCompile extension
            return false
        }

        List<List<Configuration>> terminalPaths = []

        def confPaths
        confPaths = { Configuration c, List<Configuration> path ->
            def subconfs = project.configurations.findAll { it.extendsFrom.any { it == c } }
            for (Configuration subconf in subconfs)
                confPaths(subconf, path + [c])
            if (subconfs.isEmpty()) {
                terminalPaths.add(path + [c])
            }
        }
        confPaths(project.configurations.getByName(confName), [])

        def sourceSetConfs = sourceSetCompileConfigurations()

        // if any configuration path does NOT contain a sourceSet conf in it somewhere, it is a runtime-only concern
        return terminalPaths.any { path ->
            !path.any { sourceSetConfs.contains(it.name) }
        }
    }

    /**
     * @param confName
     * @return a list of extending configurations identified as compile configurations for some source set, in order
     * of nearest to furthest to confName in terms of ancestry
     */
    @Memoized
    List<Configuration> parentSourceSetConfigurations(String confName) {
        // we go up the configuration hierarchy here, because we want to identify dependencies that were defined in
        // higher configurations but belong here (e.g. defined in runtime, but in use in compile)
        List<Configuration> sourceSetConfs = []
        def extendingConfs
        extendingConfs = { Configuration c ->
            for (Configuration subconf in project.configurations.findAll { it.extendsFrom.any { it == c } }) {
                def sourceSet = sourceSetCompileConfigurations().find { it == subconf.name }
                if (sourceSet)
                    sourceSetConfs.add(subconf)
                extendingConfs(subconf)
            }
        }
        extendingConfs(project.configurations.getByName(confName))

        return sourceSetConfs
    }

    @Memoized
    Set<ModuleIdentifier> usedDependencies(String confName) {
        def references = classReferences(confName)
        return !references ? Collections.emptySet() :
                (references.indirect + references.direct)*.moduleVersion*.id*.module.toSet()
    }

    /**
     * @param confName
     * @return the set of unused dependencies; always empty when there are no compiled classes to evaluate in the nearest
     * source set to avoid false positives
     */
    @Memoized
    Set<ModuleIdentifier> unusedDependencies(String confName) {
        def references = classReferences(confName)
        if (!references)
            return Collections.emptySet()

        def conf = project.configurations.getByName(confName)
        try {
            def unused = firstLevelDependenciesInConf(conf)

            // remove all directly used dependencies
            unused.removeAll(references.direct.collect { it.moduleVersion.id })

            // dependencies that are indirectly used but not present in the transitive graph
            def neededBecauseOfAnIndirectRef = references.indirect.collect { it.moduleVersion.id }
            conf.resolvedConfiguration.firstLevelModuleDependencies.each { d ->
                neededBecauseOfAnIndirectRef.removeAll(transitiveDependencies(d))
            }

            // remove all indirectly used dependencies
            unused.removeAll(neededBecauseOfAnIndirectRef)

            return unused.collect { it.module }.toSet()
        } catch (IllegalStateException ignored) {
            return [] as Set
        }
    }

    /**
     * @param confName
     * @return first level module dependencies (with resolved versions) declared to this conf directly, and
     * not to extended configurations
     */
    @Memoized
    Set<ModuleVersionIdentifier> firstLevelDependenciesInConf(Configuration conf) {
        def dependencies = conf.resolvedConfiguration.firstLevelModuleDependencies.collect { it.module.id }
        def declared = conf.dependencies.collect { new DefaultModuleIdentifier(it.group, it.name) }
        dependencies.retainAll { declared.contains(it.module) }
        dependencies.toSet()
    }

    /**
     * @param dep
     * @return the entire recursive transitive closure of <code>dep</code>, not including <code>dep</code>
     */
    @Memoized
    Collection<ResolvedDependency> transitiveDependencies(ResolvedDependency dep) {
        def transitives = new HashSet()
        def recurseTransitives = { Collection<ResolvedDependency> ds ->
            ds.each { d ->
                if (d.module.id != dep.module.id && transitives.add(d))
                    owner.call(d.children)
            }
        }
        recurseTransitives(dep.children)
        return transitives
    }

    boolean isResolved(String conf) {
        try {
            return isResolved(project.configurations.getByName(conf))
        } catch (UnknownConfigurationException ignored) {
            return false
        }
    }

    boolean isResolved(Configuration conf) {
        // Gradle does not properly propagate the resolved state down configuration hierarchies
        conf.state == Configuration.State.RESOLVED ||
                project.configurations.findAll { it.extendsFrom.contains(conf) }.any { isResolved(it) }
    }

    boolean isResolvable(String conf) {
        try {
            return isResolvable(project.configurations.getByName(conf))
        } catch (UnknownConfigurationException ignored) {
            return false
        }
    }

    boolean isResolvable(Configuration conf) {
        // isCanBeResolved was added in Gradle 3.3. Previously, all configurations were resolvable
        if (Configuration.class.declaredMethods.any { it.name == 'isCanBeResolved' }) {
            return conf.canBeResolved
        }
        return true
    }

    Set<Configuration> allExtendsFrom(Configuration conf) {
        def extendsFromRecurse = { Configuration c ->
            c.extendsFrom + c.extendsFrom.collect { owner.call(it) }.flatten()
        }
        return extendsFromRecurse(conf)
    }

    private class DependencyReferences {
        Set<ResolvedArtifact> direct = new HashSet()
        Set<ResolvedArtifact> indirect = new HashSet()
    }

    private Iterable<String> sourceSetCompileConfigurations() {
        (project.convention.getPlugin(JavaPluginConvention).sourceSets +
                (project.getExtensions().findByName('android')?.sourceSets ?: []))*.compileConfigurationName
    }

    SourceSet sourceSetByConf(String conf) {
        project.convention.findPlugin(JavaPluginConvention)?.sourceSets?.find { it.compileConfigurationName == conf } ?:
                project.configurations.findAll { it.extendsFrom.contains(project.configurations.getByName(conf)) }
                        .collect { sourceSetByConf(it.name) }
                        .find { true } // get the first source set, if one is available that matches
    }

    private Iterable<File> sourceSetClasspath(String conf) {
        def sourceSet = sourceSetByConf(conf == 'compileOnly' ? 'compile' : conf)
        if (sourceSet) return sourceSet.compileClasspath

        // android
        if (conf.startsWith('test'))
            return project.tasks.findByName('compileReleaseUnitTestJavaWithJavac')?.classpath
        return project.tasks.findByName('compileReleaseJavaWithJavac')?.classpath
    }

    private File sourceSetOutput(String conf) {
        def sourceSet = sourceSetByConf(conf == 'compileOnly' ? 'compile' : conf)
        if (sourceSet) return sourceSet.output.classesDir

        // all android confs get squashed into either debug or release output dir?
        if (conf.startsWith('test')) {
            def androidTestDebugOutput = project.tasks.findByName('compileDebugUnitTestJavaWithJavac')?.destinationDir
            if (androidTestDebugOutput && androidTestDebugOutput.exists()) return androidTestDebugOutput

            def androidTestReleaseOutput = project.tasks.findByName('compileReleaseUnitTestJavaWithJavac')?.destinationDir
            return androidTestReleaseOutput
        }

        def androidDebugOutput = project.tasks.findByName('compileDebugJavaWithJavac')?.destinationDir
        if (androidDebugOutput && androidDebugOutput.exists()) return androidDebugOutput

        def androidReleaseOutput = project.tasks.findByName('compileReleaseJavaWithJavac')?.destinationDir
        return androidReleaseOutput
    }
}
