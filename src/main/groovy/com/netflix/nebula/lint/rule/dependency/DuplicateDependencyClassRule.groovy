package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleDependency
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ResolvedDependency

class DuplicateDependencyClassRule extends AbstractDependencyReportRule {
    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def matchesGradleDep = { ResolvedDependency d -> d.module.id.group == dep.group && d.module.id.name == dep.name }
        def dependencyClasses = report.dependenciesByClass.findAll { it.value.find(matchesGradleDep) }
        def dupeDependencyClasses = dependencyClasses.findAll { it.value.size() > 1 }
        def dupeClassesByDependency = new HashMap<ResolvedDependency, Set<String>>().withDefault { [] as Set }
        dupeDependencyClasses.each { className, resolvedDependencies ->
            resolvedDependencies.each { dependency ->
                dupeClassesByDependency.get(dependency).add(className)
            }
        }
        if (!dupeClassesByDependency.isEmpty() && matchesGradleDep(dupeClassesByDependency.keySet().first())) {
            dupeClassesByDependency.each { resolvedDependency, classes ->
                if (!matchesGradleDep(resolvedDependency)) {
                    addLintViolation("${dep.group}:${dep.name}:${dep.version} in configuration '$conf' has ${classes.size()} classes duplicated by ${resolvedDependency.name}", GradleViolation.Level.Warning)
                }
            }
        }
    }
}
