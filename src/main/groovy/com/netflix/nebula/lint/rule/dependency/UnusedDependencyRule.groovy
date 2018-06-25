package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.plugins.JavaPluginConvention

class UnusedDependencyRule extends GradleLintRule implements GradleModelAware {
    String description = 'remove unused dependencies, relocate dependencies to the correct configuration, and ensure that directly used transitives are declared as first order dependencies'
    static final List<String> shouldBeRuntime = ['xerces', 'xercesImpl', 'xml-apis']

    Map<ModuleIdentifier, MethodCallExpression> runtimeDependencyDefinitions = [:]
    Set<ModuleIdentifier> compileOnlyDependencies = [] as Set
    
    DependencyService dependencyService

    @Override
    protected void beforeApplyTo() {
        dependencyService = DependencyService.forProject(project)
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if (!dependencyService.isResolved(conf)) {
            return // we won't slow down the build by resolving the configuration if it hasn't been already
        }

        if(project.convention.findPlugin(JavaPluginConvention)) {
            def mid = dep.toModule()

            if(conf == 'compileOnly') {
                compileOnlyDependencies.add(mid)
            }

            if (!dependencyService.isRuntime(conf) && dependencyService.isResolvable(conf)) {
                def jarContents = dependencyService.jarContents(mid)
                if (!jarContents) {
                    return // dependency being substituted by resolution rule?
                }
                if (jarContents.isWebjar) {
                    addBuildLintViolation('webjars should be in the runtime configuration', call)
                            .replaceWith(call, "runtime '${dep.toNotation()}'")
                } else if (jarContents.nothingButMetaInf) {
                    addBuildLintViolation('this dependency should be removed since its artifact is empty', call)
                            .delete(call)
                } else if (jarContents.classes.isEmpty()) {
                    // webjars, resource bundles, etc
                    addBuildLintViolation("this dependency should be moved to the runtime configuration since it has no classes", call)
                            .replaceWith(call, "runtime '${dep.toNotation()}'")
                } else if (shouldBeRuntime.contains(dep.name)) {
                    addBuildLintViolation("this dependency should be moved to the runtime configuration", call)
                            .replaceWith(call, "runtime '${dep.toNotation()}'")
                } else if (dependencyService.unusedDependencies(conf).contains(mid)) {
                    def requiringSourceSet = dependencyService.parentSourceSetConfigurations(conf)
                            .find { parent -> dependencyService.usedDependencies(parent.name).contains(mid) }

                    if (jarContents.isServiceProvider) {
                        addBuildLintViolation("this dependency is a service provider unused at compile time and can be moved to the runtime configuration", call)
                                .replaceWith(call, "runtime '${dep.toNotation()}'")
                    }
                    // is there some extending configuration that needs this dependency?
                    if (requiringSourceSet && !dependencyService.firstLevelDependenciesInConf(requiringSourceSet)
                            .collect { it.module }.contains(mid) && conf != 'compileOnly') {
                        // never move compileOnly dependencies
                        addBuildLintViolation("this dependency should be moved to configuration $requiringSourceSet.name", call)
                                .replaceWith(call, "${requiringSourceSet.name} '${dep.toNotation()}'")
                    } else {
                        addBuildLintViolation('this dependency is unused and can be removed', call)
                                .delete(call)
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
    protected void visitClassComplete(ClassNode node) {
        def dependenciesBlock = bookmark('dependencies')

        Set<ModuleVersionIdentifier> insertedDependencies = [] as Set

        def convention = project.convention.findPlugin(JavaPluginConvention)
        if(convention) {
            // sort the sourceSets from least dependent to most dependent, e.g. [main, test, integTest]
            def sortedSourceSets = convention.sourceSets.sort(false, dependencyService.sourceSetComparator())

            sortedSourceSets.each { sourceSet ->
                def confName = sourceSet.compileConfigurationName
                dependencyService.undeclaredDependencies(confName).each { undeclared ->
                    def runtimeDeclaration = runtimeDependencyDefinitions[undeclared.module]
                    // TODO this may be too specialized, should we just be moving deps down conf hierarchies as necessary?
                    if (runtimeDeclaration) {
                        addBuildLintViolation("this dependency should be moved to configuration $confName", runtimeDeclaration)
                                .replaceWith(runtimeDeclaration, "$confName '$undeclared'")
                    } else if(!compileOnlyDependencies.contains(undeclared.module)) {
                        // only add the dependency in the lowest configuration that requires it
                        if(insertedDependencies.add(undeclared)) {
                            addBuildLintViolation("one or more classes in $undeclared are required by your code directly")
                                    .insertIntoClosure(dependenciesBlock, "$confName '$undeclared'")
                        }
                    }
                }
            }
        }
    }
}
