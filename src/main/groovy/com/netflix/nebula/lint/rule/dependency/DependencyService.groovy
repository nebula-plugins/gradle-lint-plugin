package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.interop.GradleKt
import com.netflix.nebula.lint.SourceSetUtils
import com.netflix.nebula.lint.VersionNumber
import groovy.transform.Memoized
import groovyx.gpars.GParsPool
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.objectweb.asm.ClassReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Supplier
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipException

class DependencyService {
    private static final String EXTENSION_NAME = "gradleLintDependencyService"
    private static final Collection<String> DEFAULT_METHOD_REFERENCE_IGNORED_PACKAGES = ["java/lang", "java/util", "java/net", "java/io", "java/nio", "java/swing"]
    //ignore java packages by default for method references

    static synchronized DependencyService forProject(Project project) {
        def extension = project.extensions.findByType(DependencyServiceExtension)
        if (!extension) {
            extension = project.extensions.create(EXTENSION_NAME, DependencyServiceExtension)
        }
        if (extension.dependencyService == null) {
            extension.dependencyService = new DependencyService(project)
        }
        return extension.dependencyService
    }

    static synchronized void removeForProject(Supplier<Project> projectSupplier) {
        Project project = projectSupplier.get()
        def extension = project.extensions.findByType(DependencyServiceExtension)
        if (extension) {
            extension.dependencyService = null
        }
    }
    static synchronized void removeForProject(Project project) {
        removeForProject({ project } as Supplier<Project>)
    }

    static class DependencyServiceExtension {
        DependencyService dependencyService
    }

    private final Logger logger = LoggerFactory.getLogger(DependencyService)

    Project project

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
            if (c.canBeResolved) {
                terminalConfs.add(c)
            } else {
                Set<Configuration> parentConfigs = project.configurations.findAll { it.name != 'default' && it.extendsFrom.any { it == c } }
                //def subconfs = resolvableConfigurations()
                for (Configuration parent in parentConfigs) {
                    extendingConfs(parent)
                }
            }
        }
        extendingConfs(conf)

        def artifactsByClass = [:].withDefault { [] }
        def artifactsToScan = terminalConfs.collect { Configuration terminalConf ->
            if (isResolvable(terminalConf)) {
                return terminalConf.resolvedConfiguration.resolvedArtifacts
            } else {
                Configuration parent = terminalConf.extendsFrom.find { isResolvable(it) }
                if (parent) {
                    return findAndReplaceDeprecatedConfiguration(parent).resolvedConfiguration.resolvedArtifacts                    
                }

            }
        }.flatten().grep()

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
     * Replaces compile configuration for compileClasspath so we find proper usage of dependencies
     * @param configuration
     * @return
     */
    protected Configuration findAndReplaceDeprecatedConfiguration(Configuration configuration) {
        Configuration replacementConfiguration = null
        if (configuration.name == 'compile') {
            replacementConfiguration = project.configurations.findByName('compileClasspath')
        } else if (configuration.name.endsWith('CompileOnly') || configuration.name.endsWith('Compile') || configuration.name.endsWith('Implementation')) {
            replacementConfiguration = project.configurations.findByName(
                    configuration.name.replaceAll('CompileOnly', '')
                            .replaceAll('Compile', '')
                            .replaceAll('Implementation', '') + 'CompileClasspath')
        } else if (configuration.name.endsWith('RuntimeOnly') || configuration.name.endsWith('Runtime')) {
            replacementConfiguration = project.configurations.findByName(
                    configuration.name.replaceAll('RuntimeOnly', '')
                            .replaceAll('Runtime', '') + 'RuntimeClasspath')
        }

        if (!replacementConfiguration) {
            replacementConfiguration = configuration
        }
        return replacementConfiguration
    }

    /**
     * Where possible, replaces common non-resolvable configurations with resolvable configurations
     * @param configuration
     * @return
     */
    protected Configuration findAndReplaceNonResolvableConfiguration(Configuration configuration) {
        Configuration replacementConfiguration = findAndReplaceDeprecatedConfiguration(configuration)
        Configuration replacementClasspathConfiguration

        if(replacementConfiguration.name == 'api' && project.plugins.hasPlugin('java-platform')) {
            project.configurations.create('test')
        } else if (replacementConfiguration.name == 'api' || replacementConfiguration.name == 'implementation' || replacementConfiguration.name == 'compileOnly') {
            replacementClasspathConfiguration = project.configurations.findByName('compileClasspath')
        } else if (replacementConfiguration.name == 'runtime' || replacementConfiguration.name == 'runtimeOnly') {
            replacementClasspathConfiguration = project.configurations.findByName('runtimeClasspath')
        } else if (!isResolvable(replacementConfiguration)) {
            replacementClasspathConfiguration = getResolvableParent(configuration.name)
        }
        if (replacementClasspathConfiguration != null) {
            replacementConfiguration = replacementClasspathConfiguration
        }

        if (!isResolvable(replacementConfiguration)) {
            project.logger.debug("Configuration ${replacementConfiguration.name} is non-resolvable but will be used for lack of an alternative")
        }

        return replacementConfiguration
    }

    /**
     * @param id
     * @return the contents of an artifact matching this module version, or <code>null</code> if no such
     * artifact was found on the resolved classpath
     */
    JarContents jarContents(ModuleIdentifier id) {
        def configurations = resolvableConfigurations()
        for (Configuration conf in configurations) {
            def artifact = conf.resolvedConfiguration.resolvedArtifacts.find { it.moduleVersion.id.module == id && it.type == 'jar' }
            if (artifact) {
                return jarContents(artifact.file)
            }
        }
        return null
    }

    synchronized Set<Configuration> resolvableConfigurations() {
        return project.configurations.findAll { isResolvable(it) }.findAll { !hasAResolutionAlternative(it) }
    }

    /**
     * Projects previously using {@link #resolvableConfigurations()} or
     * checking configurations individually with {@link #isResolved()}
     * will likely want to use this method
     */
    Set<Configuration> resolvableAndResolvedConfigurations() {
        return resolvableConfigurations().findAll { isResolved(it) }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    // Static memoization will leak
    @Memoized
    JarContents jarContents(File file) {
        if (!file.exists()) {
            return new JarContents(entryNames: Collections.emptyList())
        }

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

    public final static Comparator<ModuleVersionIdentifier> DEPENDENCY_COMPARATOR = new Comparator<ModuleVersionIdentifier>() {
        @Override
        int compare(ModuleVersionIdentifier m1, ModuleVersionIdentifier m2) {
            if (m1.group != m2.group) {
                return m1.group.compareTo(m2.group)
            } else if (m1.name != m2.name) {
                return m1.name.compareTo(m2.name)
            } else {
                return VersionNumber.parse(m2.version).compareTo(VersionNumber.parse(m1.version))
            }
        }
    }

    /**
     * Returns method references for all classes on a given configuration.
     * This doesn't ignore packages at all.
     * @param confName
     * @return
     */
    @Memoized
    Collection<ClassInformation> methodReferences(String confName) {
        return findMethodReferences(confName, Collections.EMPTY_LIST, Collections.EMPTY_LIST)
    }

    /**
     * Returns method references for all classes on a given configuration
     * By default, ignores common java package references. Example: class: Main | methodName: <init> | owner: java/lang/Object | methodDesc: ()V
     * Custom ignored packages can be provided. The check is done for packages that startWith the given values.
     * @param confName
     * @param ignoredPackages
     * @return
     */
    @Memoized
    Collection<ClassInformation> methodReferencesExcluding(String confName, Collection<String> ignoredPackages = []) {
        return findMethodReferences(confName, Collections.EMPTY_LIST, ignoredPackages + DEFAULT_METHOD_REFERENCE_IGNORED_PACKAGES)
    }

    /**
     * Returns method references for all classes on a given configuration
     * By default, ignores common java package references. Example: class: Main | methodName: <init> | owner: java/lang/Object | methodDesc: ()V
     * @param confName
     * @param includeOnlyPackages
     * @return
     */
    @Memoized
    Collection<ClassInformation> methodReferencesIncludeOnly(String confName, Collection<String> includeOnlyPackages) {
        return findMethodReferences(confName, includeOnlyPackages, Collections.EMPTY_LIST)
    }

    private Collection<ClassInformation> findMethodReferences(String confName, Collection<String> includeOnlyPackages, Collection<String> ignoredPackages) {
        Map<String, Collection<ResolvedArtifact>> artifactsByClass = artifactsByClass(confName)
        Collection<ClassInformation> classesInfo = []
        sourceSetOutput(confName).files.findAll { it.exists() }.each { output ->
            Files.walkFileTree(output.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().name.endsWith('.class')) {
                        ClassInformation classInformation = new MethodScanner().findMethodReferences(artifactsByClass, file, includeOnlyPackages, ignoredPackages)
                        classesInfo.add(classInformation)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return classesInfo
    }

    @Memoized
    private DependencyReferences classReferences(String confName) {
        def conf = project.configurations.getByName(confName)
        def classpath = sourceSetClasspath(conf)
        def references = new DependencyReferences()

        def confSourceSet = sourceSetOutput(confName)
        if (confSourceSet != null) {
            confSourceSet.files.findAll { it.exists() }.each { output ->
                def artifactsByClass = artifactsByClass(conf)
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
            }
        }
        return references
    }

    @Memoized
    Set<ModuleVersionIdentifier> undeclaredDependencies(String confName) {
        Collection<ModuleVersionIdentifier> required = findRequiredDependencies(confName)
        Collection<ModuleVersionIdentifier> declared = findDeclaredDependencies(project.configurations.getByName(confName))

        return (required - declared).toSorted(DEPENDENCY_COMPARATOR).toSet()
    }

    protected Collection<ModuleVersionIdentifier> findRequiredDependencies(String confName) {
        DependencyReferences references = classReferences(confName)
        if (!references) {
            return Collections.emptySet()
        }

        Collection<ModuleVersionIdentifier> required = (references.direct + references.indirect).collect { it.moduleVersion.id as ModuleVersionIdentifier }
                .groupBy { it.module }
                .values()
                .collect {
                    it.sort { d1, d2 -> VersionNumber.parse(d2.version).compareTo(VersionNumber.parse(d1.version)) }.first()
                }
                .toSet()
        return required
    }

    private Collection<ModuleVersionIdentifier> findDeclaredDependencies(Configuration configuration) {
        if (!configuration.isCanBeResolved()) {
            new IllegalArgumentException("Configuration ${configuration.name} is not resolvable. We expect configurations like compileClasspath for declared dependency search")
        }
        def declared = configuration.resolvedConfiguration.firstLevelModuleDependencies.collect { it.module.id }
        return declared
    }

    /**
     * A configuration that has any subconfiguration that is not a transitive superconfiguration of some
     * source set is a runtime configuration.
     *
     * Ex: provided from nebula is a superconfiguration of compile and runtime, so even though provided is a
     * compile configuration w.r.t. compile, it is a runtime configuration w.r.t. runtime
     *
     * @param conf
     * @return <code> true</code> if this is a runtime configuration, even if it is also a compile configuration
     */
    @Memoized
    boolean isRuntime(String confName) {
        if (confName == 'runtime' || confName == 'runtimeOnly') {
            return true
        }

        if (confName == 'compileOnly') {
            return false
        }

        if (confName == 'providedCompile' && project.plugins.hasPlugin('war')) {
            // Gradle should not have made providedRuntime extend from providedCompile, since the runtime conf would already
            // have inherited providedCompile dependencies via the compile -> providedCompile extension
            return false
        }

        List<List<Configuration>> terminalPaths = constructConfigurationHierarchy(confName)

        def sourceSetConfs = sourceSetCompileConfigurations()

        // if any configuration path does NOT contain a sourceSet conf in it somewhere, it is a runtime-only concern
        return terminalPaths.every { path ->
            !path.any { config ->
                sourceSetConfs.contains(config.name)
            }
        }
    }

    private List<List<Configuration>> constructConfigurationHierarchy(String startingPoint) {
        List<List<Configuration>> terminalPaths = []

        def confPaths
        confPaths = { Configuration c, List<Configuration> path ->
            def subconfs = project.configurations.findAll { it.extendsFrom.any { it == c } }
            for (Configuration subconf in subconfs) {
                confPaths(subconf, path + [c])
            }
            if (subconfs.isEmpty()) {
                terminalPaths.add(path + [c])
            }
        }
        confPaths(project.configurations.getByName(startingPoint), [])
        return terminalPaths
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
                if (sourceSet) {
                    sourceSetConfs.add(subconf)
                }
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
    Set<ModuleIdentifier> unusedDependencies(String confName, String declaredConf) {
        def references = classReferences(confName)
        if (!references) {
            return Collections.emptySet()
        }

        try {
            def resolvableConfig = project.configurations.findByName(confName)
            def unused = firstLevelDependenciesInConf(resolvableConfig, project.configurations.findByName(declaredConf))

            // remove all directly used dependencies
            unused.removeAll(references.direct.collect { it.moduleVersion.id })

            // dependencies that are indirectly used but not present in the transitive graph
            def neededBecauseOfAnIndirectRef = references.indirect.collect { it.moduleVersion.id }
            resolvableConfig.resolvedConfiguration.firstLevelModuleDependencies.each { d ->
                neededBecauseOfAnIndirectRef.removeAll(transitiveDependencies(d))
            }

            // remove all indirectly used dependencies
            unused.removeAll(neededBecauseOfAnIndirectRef)

            return unused.collect { it.module }.toSet()
        } catch (IllegalStateException ignored) {
            return [] as Set
        }
    }

    private Configuration getResolvableParent(String confName) {
        Configuration configuration = project.configurations.getByName(confName)
        if (isResolvable(confName)) {
            return configuration
        } else if (hasResolvableParentConfiguration(confName)) {
            List<List<Configuration>> configurationHierarchy = constructConfigurationHierarchy(confName)
            return configurationHierarchy.collectMany {
                it.findAll {it.canBeResolved }
            }.first()
        } else {
            return configuration
        }
    }

    /**
     * @param resolvableConfig
     * @param declaredConfig
     * @return first level module dependencies (with resolved versions) declared to this conf directly, and
     *          not to extended configurations
     */
    Set<ModuleVersionIdentifier> firstLevelDependenciesInConf(Configuration resolvableConfig, String declaredConfig) {
        return firstLevelDependenciesInConf(resolvableConfig, project.configurations.findByName(declaredConfig))
    }

    /**
     *
     * @param resolvableConfig
     * @param declaredConfig
     * @return first level module dependencies (with resolved versions) declared to this conf directly, and
     *         not to extended configurations
     */
    @Memoized
    Set<ModuleVersionIdentifier> firstLevelDependenciesInConf(Configuration resolvableConfig, Configuration declaredConfig) {
        def dependencies = resolvableConfig.resolvedConfiguration.firstLevelModuleDependencies.collect { it.module.id }
        def declared = declaredConfig.dependencies.collect { new DefaultModuleIdentifier(it.group, it.name) }
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
                if (d.module.id != dep.module.id && transitives.add(d)) {
                    owner.call(d.children)
                }
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

    static boolean isResolvable(Configuration conf) {
        // isCanBeResolved was added in Gradle 3.3. Previously, all configurations were resolvable
        if (Configuration.class.declaredMethods.any { it.name == 'isCanBeResolved' }) {
            //compileOnly and its flavors are marked as resolvable but only for backward compatibility purposes
            //in reality they shouldn't be
            return conf.canBeResolved && !conf.name.toLowerCase().endsWith("compileonly") && conf.name != "default"
        }
        return true
    }

    static boolean hasAResolutionAlternative(Configuration configuration) {
        // 'getResolutionAlternatives' is a method on DefaultConfiguration as of Gradle 6.0
        def method = configuration.metaClass.getMetaMethod('getResolutionAlternatives')
        if (method != null) {
            def alternatives = configuration.getResolutionAlternatives()
            if (alternatives != null && alternatives instanceof List<String> && alternatives.size() > 0) {
                return true
            }
        }

        return false
    }

    boolean hasResolvableParentConfiguration(String conf) {
        try {
            List<List<Configuration>> configurationHierarchy = constructConfigurationHierarchy(conf)
            return configurationHierarchy.any { List<Configuration> path ->
                path.any {it.canBeResolved }
            }
        } catch (UnknownConfigurationException ignored) {
            return false
        }
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
        (SourceSetUtils.getSourceSets(project) +
                (project.getExtensions().findByName('android')?.sourceSets ?: []))*.compileClasspathConfigurationName
    }

    SourceSet sourceSetByConf(String conf) {
        SourceSetContainer sourceSets = SourceSetUtils.getSourceSets(project)
        sourceSets?.find {
            it.compileClasspathConfigurationName == conf
        }
                ?: sourceSets?.find { sourceSet -> project.configurations.getByName(conf).extendsFrom.any { it.name == sourceSet.compileClasspathConfigurationName } } //find source set from parent
                ?: project.configurations.findAll { it.extendsFrom.contains(project.configurations.getByName(conf)) }
                .collect { sourceSetByConf(it.name) }
                .find { true } // get the first source set, if one is available that matches

    }

    private Iterable<File> sourceSetClasspath(Configuration conf) {
        def sourceSet = sourceSetByConf(resolvableConf(conf).name)
        if (sourceSet) {
            return sourceSet.compileClasspath
        }

        // android
        if (conf.name.startsWith('test')) {
            return project.tasks.findByName('compileReleaseUnitTestJavaWithJavac')?.classpath
        }
        return project.tasks.findByName('compileReleaseJavaWithJavac')?.classpath
    }

    private Configuration resolvableConf(Configuration conf) {
        findAndReplaceNonResolvableConfiguration(conf)
    }

    private FileCollection sourceSetOutput(String confName) {
        def conf = project.configurations.getByName(confName)
        def sourceSet = sourceSetByConf(resolvableConf(conf).name)
        if (sourceSet) {
            if (GradleKt.versionLessThan(project.gradle, "4.0")) {
                return project.files(sourceSet.output.classesDir)
            } else {
                return sourceSet.output.classesDirs
            }
        }

        // all android confs get squashed into either debug or release output dir?
        if (confName.startsWith('test')) {
            def androidTestDebugOutput = project.tasks.findByName('compileDebugUnitTestJavaWithJavac')?.destinationDir
            if (androidTestDebugOutput && androidTestDebugOutput.exists()) {
                return androidTestDebugOutput
            }

            def androidTestReleaseOutput = project.tasks.findByName('compileReleaseUnitTestJavaWithJavac')?.destinationDir
            return androidTestReleaseOutput
        }

        def androidDebugOutput = project.tasks.findByName('compileDebugJavaWithJavac')?.destinationDir
        if (androidDebugOutput && androidDebugOutput.exists()) {
            return androidDebugOutput
        }

        def androidReleaseOutput = project.tasks.findByName('compileReleaseJavaWithJavac')?.destinationDir
        return androidReleaseOutput
    }

    Comparator<? super SourceSet> sourceSetComparator() {
        // sort the sourceSets from least dependent to most dependent, e.g. [main, test, integTest]
        new Comparator<SourceSet>() {
            @Override
            int compare(SourceSet s1, SourceSet s2) {
                def c1 = project.configurations.findByName(s1.compileClasspathConfigurationName)
                def c2 = project.configurations.findByName(s2.compileClasspathConfigurationName)

                if (allExtendsFrom(c1).contains(c2)) {
                    1
                }
                if (allExtendsFrom(c2).contains(c1)) {
                    -1
                }
                // secondary sorting if there is no relationship between these source sets
                else {
                    c1.name <=> c2.name
                }
            }
        }
    }
}