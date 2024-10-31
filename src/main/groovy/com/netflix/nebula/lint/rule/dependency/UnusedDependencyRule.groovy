package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.SourceSetUtils
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.SourceSet

class UnusedDependencyRule extends GradleLintRule implements GradleModelAware {
    String description = 'remove unused dependencies, relocate dependencies to the correct configuration, and ensure that directly used transitives are declared as first order dependencies'
    static final List<String> shouldBeRuntime = ['xerces', 'xercesImpl', 'xml-apis']

    Map<ModuleIdentifier, MethodCallExpression> runtimeDependencyDefinitions = [:]
    Set<ModuleIdentifier> compileOnlyDependencies = [] as Set

    DependencyService dependencyService

    Collection<UnusedDependencyDeclaration> unusedDependencies = new ArrayList<>()
    Map<String, Collection<ModuleIdentifier>> declaredDependenciesByConf = new HashMap<>()

    @Override
    protected void beforeApplyTo() {
        dependencyService = DependencyService.forProject(project)
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String declaredConf, GradleDependency dep) {
        if (ignored) { 
            return // short-circuit ignored dependencies
        }
        String conf = dependencyService.findAndReplaceNonResolvableConfiguration(project.configurations.getByName(declaredConf)).name

        if(SourceSetUtils.hasSourceSets(project)) {
            def mid = dep.toModule()

            if (!declaredDependenciesByConf.containsKey(declaredConf)) {
                declaredDependenciesByConf.put(declaredConf, new ArrayList<ModuleIdentifier>())
            }
            declaredDependenciesByConf.get(declaredConf).add(mid)

            if (declaredConf == 'compileOnly') {
                compileOnlyDependencies.add(mid)
            }

            if (!dependencyService.isRuntime(conf) && (dependencyService.isResolvable(conf) || dependencyService.hasResolvableParentConfiguration(conf))) {
                def jarContents = dependencyService.jarContents(mid)
                if (!jarContents) {
                    return // dependency being substituted by resolution rule?
                }
                if (jarContents.isWebjar) {
                    addBuildLintViolation('webjars should be in the runtimeOnly configuration', call)
                } else if (jarContents.nothingButMetaInf) {
                    addBuildLintViolation('this dependency should be removed since its artifact is empty', call)
                } else if (jarContents.classes.isEmpty()) {
                    // webjars, resource bundles, etc
                    addBuildLintViolation("this dependency should be moved to the runtimeOnly configuration since it has no classes", call)
                } else if (shouldBeRuntime.contains(dep.name)) {
                    addBuildLintViolation("this dependency should be moved to the runtimeOnly configuration", call)
                } else if (dependencyService.unusedDependencies(conf, declaredConf).contains(mid)) {
                    def requiringSourceSet = dependencyService.parentSourceSetConfigurations(conf)
                            .find { parent -> dependencyService.usedDependencies(parent.name).contains(mid) }

                    if (jarContents.isServiceProvider) {
                        addBuildLintViolation("this dependency is a service provider unused at compileClasspath time and can be moved to the runtimeOnly configuration", call)
                    }
                    // is there some extending configuration that needs this dependency?
                    if (requiringSourceSet && !dependencyService.firstLevelDependenciesInConf(requiringSourceSet, conf)
                            .collect { it.module }.contains(mid) && conf != 'compileOnly') {
                        // never move compileOnly dependencies
                        addBuildLintViolation("this dependency should be moved to configuration $requiringSourceSet.name", call)
                    } else {
                        unusedDependencies.add(new UnusedDependencyDeclaration(conf, mid, dep, 'this dependency is unused and can be removed', call))
                    }
                }
            } else if (conf != 'compileOnly') {
                runtimeDependencyDefinitions[mid] = call
            }
        }
    }

    @Override
    void visitDependencies(MethodCallExpression call) {
        bookmark('dependencies', call)
    }

    @Override
    void visitClassComplete(ClassNode node) {
        Set<ModuleVersionIdentifier> insertedDependencies = [] as Set

        if(SourceSetUtils.hasSourceSets(project)) {
            // sort the sourceSets from least dependent to most dependent, e.g. [main, test, integTest]
            def sortedSourceSets = SourceSetUtils.getSourceSets(project).sort(false, dependencyService.sourceSetComparator())

            sortedSourceSets.each { sourceSet ->
                def confName = sourceSet.compileClasspathConfigurationName
                dependencyService.undeclaredDependencies(confName).each { undeclared ->
                    def runtimeDeclaration = runtimeDependencyDefinitions[undeclared.module]
                    // TODO this may be too specialized, should we just be moving deps down conf hierarchies as necessary?
                    if (runtimeDeclaration) {
                        addBuildLintViolation("this dependency should be moved to configuration ${declarationConfigurationName(confName)}", runtimeDeclaration)
                    } else if (!compileOnlyDependencies.contains(undeclared.module)) {
                        // only add the dependency in the lowest configuration that requires it
                        if (insertedDependencies.add(undeclared)) {
                            addBuildLintViolation("one or more classes in $undeclared are required by your code directly")
                            // TODO: insert the undeclared dependency for the correct project/configuration
                        }
                    }
                }
            }

            handleDependenciesUsedInOtherConfigurations(sortedSourceSets)
        }
    }

    private void handleDependenciesUsedInOtherConfigurations(List<SourceSet> sortedSourceSets) {
        ArrayList<UsedElsewhereDependencyDeclaration> usedElsewhere =
                collectUsedElsewhereDependenciesAndAddViolationsToTrueUnusedDependencies(sortedSourceSets)

        ArrayList<UsedElsewhereDependencyDeclaration> filteredUsedElsewhere = removeDuplicatedChildrenConfigurations(usedElsewhere)

        filteredUsedElsewhere.each { declaration ->
            String dependencyDeclarationConfName = declarationConfigurationName(declaration.confNameRequiringDep)

            boolean dependencyAlreadyListed = declaredDependenciesByConf.get(dependencyDeclarationConfName)?.contains(declaration.moduleIdentifier)
            if (dependencyAlreadyListed) {
                addBuildLintViolation("this dependency should is already added to the needed configuration $dependencyDeclarationConfName and can be removed from ${declaration.configurationName}", declaration.call)
                        .delete(declaration.call)
            } else {
                String versionAddition = declaration.gradleDependency.version != null ? ":${declaration.gradleDependency.version}" : ''
                addBuildLintViolation("this dependency should be moved to configuration $dependencyDeclarationConfName", declaration.call)
                        .replaceWith(declaration.call, "$dependencyDeclarationConfName '${declaration.moduleIdentifier}$versionAddition'")
            }

        }
    }

    private ArrayList<UsedElsewhereDependencyDeclaration> collectUsedElsewhereDependenciesAndAddViolationsToTrueUnusedDependencies(List<SourceSet> sortedSourceSets) {
        Collection<UsedElsewhereDependencyDeclaration> usedElsewhere = new ArrayList<>()
        Collection<UnusedDependencyDeclaration> falseAlarmUnusedDependencies = new ArrayList<>()

        sortedSourceSets.collect { it.compileClasspathConfigurationName }.each { confName ->
            Collection<ModuleVersionIdentifier> requiredDependencies = dependencyService.findRequiredDependencies(confName)
            requiredDependencies.each { required ->
                Collection<UnusedDependencyDeclaration> falseAlarmDepsForConf = unusedDependencies.findAll { unused ->
                    unused.moduleIdentifier == required.module
                }
                falseAlarmDepsForConf.each {
                    usedElsewhere.add(new UsedElsewhereDependencyDeclaration(it.configurationName, it.moduleIdentifier, it.gradleDependency, it.message, it.call, confName))

                    falseAlarmUnusedDependencies.addAll(falseAlarmDepsForConf)
                }
            }
        }

        // add lint violation on remaining true unused dependencies
        (unusedDependencies - falseAlarmUnusedDependencies).each { declaration ->
            addBuildLintViolation(declaration.message, declaration.call)
                    .delete(declaration.call)
        }
        return usedElsewhere
    }

    /**
     * If the required dependency is listed in multiple configurations in a hierarchy, then keep the parent configuration
     */
    private ArrayList<UsedElsewhereDependencyDeclaration> removeDuplicatedChildrenConfigurations(ArrayList<UsedElsewhereDependencyDeclaration> usedElsewhere) {
        usedElsewhere.groupBy { it.moduleIdentifier }.each { declarationByDep ->
            Collection<UsedElsewhereDependencyDeclaration> declarations = declarationByDep.getValue()

            Collection confsRequiringDep = declarations.collect { it.confNameRequiringDep }
            declarations.each { declaration ->
                Configuration conf = project.configurations.getByName(declaration.confNameRequiringDep)

                if (conf.extendsFrom.name.any { parentConfigName -> confsRequiringDep.contains(parentConfigName) }) {
                    usedElsewhere.remove(declaration)
                }
            }
        }
        return usedElsewhere
    }

    class UnusedDependencyDeclaration {
        String configurationName
        ModuleIdentifier moduleIdentifier
        String message
        GradleDependency gradleDependency
        MethodCallExpression call

        UnusedDependencyDeclaration(String configurationName, ModuleIdentifier moduleIdentifier, GradleDependency gradleDependency, String message, MethodCallExpression call) {
            this.configurationName = configurationName
            this.moduleIdentifier = moduleIdentifier
            this.gradleDependency = gradleDependency
            this.message = message
            this.call = call
        }
    }

    class UsedElsewhereDependencyDeclaration extends UnusedDependencyDeclaration {
        String confNameRequiringDep

        UsedElsewhereDependencyDeclaration(String originalConfName, ModuleIdentifier moduleIdentifier, GradleDependency gradleDependency, String message, MethodCallExpression call, String confNameRequiringDep) {
            super(originalConfName, moduleIdentifier, gradleDependency, message, call)
            this.confNameRequiringDep = confNameRequiringDep
        }
    }

    private static String declarationConfigurationName(String configName) {
        return configName
                .replace('compileClasspath', 'implementation')
                .replace('CompileClasspath', 'Implementation')
    }
}
