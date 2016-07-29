package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier

class DuplicateDependencyClassRule extends GradleLintRule implements GradleModelAware {
    String description = 'classpaths with duplicate classes may break unpredictably depending on the order in which dependencies are provided to the classpath'

    boolean inDependencies = false
    Set<Configuration> directlyUsedConfigurations = [] as Set
    
    @Override
    void visitDependencies(MethodCallExpression call) {
        inDependencies = true
        visitMethodCallExpression(call)
        inDependencies = false
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(inDependencies) {
            def conf = project.configurations.findByName(call.methodAsString)
            if(conf) 
                directlyUsedConfigurations.add(conf)
        }
        super.visitMethodCallExpression(call)
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        for(Configuration conf: directlyUsedConfigurations) {
            if(!DependencyService.forProject(project).isResolved(conf))
                continue

            conf.resolvedConfiguration.firstLevelModuleDependencies.each { resolved ->
                checkForDuplicates (resolved.module.id, conf.name)
            }
        }
    }

    private void checkForDuplicates(ModuleVersionIdentifier mvid, String conf) {
        def dependencyService = DependencyService.forProject(project)
        
        def dependencyClasses = dependencyService.jarContents(mvid)?.classes
        if (!dependencyClasses)
            return

        def dupeDependencyClasses = dependencyService.artifactsByClass(conf)
                .findAll { dependencyClasses.contains(it.key) && it.value.size() > 1 }

        def dupeClassesByDependency = new TreeMap<ModuleVersionIdentifier, Set<String>>(DependencyService.DEPENDENCY_COMPARATOR).withDefault {
            [] as Set
        }
        dupeDependencyClasses.each { className, resolvedArtifacts ->
            resolvedArtifacts.each { artifact ->
                dupeClassesByDependency.get(artifact.moduleVersion.id).add(className)
            }
        }

        if (!dupeClassesByDependency.isEmpty() && mvid == dupeClassesByDependency.keySet().first()) {
            dupeClassesByDependency.each { resolvedMvid, classes ->
                if (mvid != resolvedMvid) {
                    addBuildLintViolation("$mvid in configuration '$conf' has ${classes.size()} classes duplicated by ${resolvedMvid}")
                }
            }
        }
    }
}
