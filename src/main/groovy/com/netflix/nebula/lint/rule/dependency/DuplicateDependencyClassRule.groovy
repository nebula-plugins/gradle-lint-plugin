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

        def dependencyClasses = dependencyService.classes(mvid)
        def dupeDependencyClasses = dependencyService.artifactsByClass(conf)
                .findAll { dependencyClasses.contains(it.key) && it.value.size() > 1 }

        def dupeClassesByDependency = new TreeMap<ModuleVersionIdentifier, Set<String>>(dependencyComparator).withDefault { [] as Set }
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

    protected Comparator<ModuleVersionIdentifier> dependencyComparator = new Comparator<ModuleVersionIdentifier>() {
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
}
