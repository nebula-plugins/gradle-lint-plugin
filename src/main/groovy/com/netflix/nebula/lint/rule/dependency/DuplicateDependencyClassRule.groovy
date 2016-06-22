package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator

class DuplicateDependencyClassRule extends GradleLintRule implements GradleModelAware {
    String description = 'classpaths with duplicate classes may break unpredictably depending on the order in which dependencies are provided to the classpath'

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def mvid = dep.toModuleVersion()
        def dependencyService = DependencyService.forProject(project)

        if(!dependencyService.isResolved(conf)) {
            return // we won't slow down the build by resolving the configuration if it hasn't been already
        }

        def dependencyClasses = dependencyService.jarContents(mvid)?.classes
        if(!dependencyClasses)
            return

        def dupeDependencyClasses = dependencyService.artifactsByClass(conf)
                .findAll { dependencyClasses.contains(it.key) && it.value.size() > 1 }

        def dupeClassesByDependency = new TreeMap<ModuleVersionIdentifier, Set<String>>(DependencyService.DEPENDENCY_COMPARATOR).withDefault { [] as Set }
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
