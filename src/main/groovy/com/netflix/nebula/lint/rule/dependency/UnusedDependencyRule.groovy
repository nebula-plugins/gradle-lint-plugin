package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.plugins.JavaPluginConvention

class UnusedDependencyRule extends GradleLintRule implements GradleModelAware {
    String description = 'remove unused dependencies, relocate dependencies to the correct configuration, and ensure that directly used transitives are declared as first order dependencies'
    static final List<String> shouldBeRuntime = ['xerces', 'xercesImpl', 'xml-apis']

    Map<ModuleVersionIdentifier, MethodCallExpression> runtimeDependencyDefinitions = [:]
    DependencyService dependencyService

    @Override
    protected void beforeApplyTo() {
        dependencyService = DependencyService.forProject(project)
    }

    private static String replaceDependencyWith(MethodCallExpression call, String conf, ModuleVersionIdentifier dep) {
        // TODO deal with call expressions that may have closure arguments or map arguments
        return "$conf '$dep'"
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def mvid = dep.toModuleVersion()
        if(!dependencyService.isRuntime(conf)) {
            def jarContents = dependencyService.jarContents(mvid)
            if(jarContents.isWebjar) {
                addBuildLintViolation('webjars should be in the runtime configuration', call)
                    .replaceWith(call, replaceDependencyWith(call, 'runtime', mvid))
            }
            else if(jarContents.nothingButMetaInf) {
                addBuildLintViolation('this dependency should be removed since its artifact is empty', call)
                    .delete(call)
            }
            else if(jarContents.classes.isEmpty()) {
                // webjars, resource bundles, etc
                addBuildLintViolation("this dependency should be moved to the runtime configuration since it has no classes", call)
                        .replaceWith(call, replaceDependencyWith(call, 'runtime', mvid))
            }
            else if(shouldBeRuntime.contains(dep.name)) {
                addBuildLintViolation("this dependency should be moved to the runtime configuration", call)
                        .replaceWith(call, replaceDependencyWith(call, 'runtime', mvid))
            }
            else if(dependencyService.unusedDependencies(conf).contains(mvid)) {
                def requiringSourceSet = dependencyService.parentSourceSetConfigurations(conf)
                        .find { parent -> dependencyService.usedDependencies(parent.name).contains(mvid) }

                if(jarContents.isServiceProvider) {
                    addBuildLintViolation("this dependency is a service provider unused at compile time and can be moved to the runtime configuration", call)
                            .replaceWith(call, replaceDependencyWith(call, 'runtime', mvid))
                }
                // is there some extending configuration that needs this dependency?
                if(requiringSourceSet && !dependencyService.firstLevelDependenciesInConf(requiringSourceSet).contains(mvid)) {
                    addBuildLintViolation("this dependency should be moved to configuration $requiringSourceSet.name", call)
                            .replaceWith(call, replaceDependencyWith(call, requiringSourceSet.name, mvid))
                }
                else {
                    addBuildLintViolation('this dependency is unused and can be removed', call)
                            .delete(call)
                }
            }
        } else {
            runtimeDependencyDefinitions[mvid] = call
        }
    }

    @Override
    void visitDependencies(MethodCallExpression call) {
        bookmark('dependencies', call)
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        def dependenciesBlock = bookmark('dependencies')

        def convention = project.convention.findPlugin(JavaPluginConvention)
        if(convention) {
            convention.sourceSets.each { sourceSet ->
                def conf = sourceSet.compileConfigurationName
                dependencyService.undeclaredDependencies(conf).each { dep ->
                    def runtimeDeclaration = runtimeDependencyDefinitions[dep]
                    // TODO this may be too specialized, should we just be moving deps down conf hierarchies as necessary?
                    if (runtimeDeclaration) {
                        addBuildLintViolation("this dependency should be moved to configuration $conf", runtimeDeclaration)
                                .replaceWith(runtimeDeclaration, replaceDependencyWith(runtimeDeclaration, conf, dep))
                    } else {
                        addBuildLintViolation("one or more classes in $dep are required by your code directly")
                                .insertIntoClosure(dependenciesBlock, "$conf '$dep'")
                    }
                }
            }
        }
    }
}
