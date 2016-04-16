package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class UnusedDependencyRule extends GradleLintRule implements GradleModelAware {
    Logger logger = LoggerFactory.getLogger("rule")
    UnusedDependencyReport report

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        fetchUnusedDependencyReportIfNecessary()

        def matchesGradleDep = { ResolvedDependency d -> d.module.id.group == dep.group && d.module.id.name == dep.name }
        def match

        logger.info("With no classes: " + report.firstOrderDependenciesWithNoClasses.collect { it.module.id.toString() }.join(','))

        if ((match = report.firstOrderDependenciesWithNoClasses.find(matchesGradleDep))) {
            addLintViolation("this dependency should be moved to the runtime configuration since it has no classes", call)
                    .replaceWith(call, "runtime '$match.module.id'")
        } else if (report.firstOrderDependenciesToRemove.find(matchesGradleDep)) {
            addLintViolation('this dependency is unused and can be removed', call).delete(call)
        } else if ((match = report.firstOrderDependenciesWhoseConfigurationNeedsToChange.keySet().find(matchesGradleDep))) {
            def toConf = report.firstOrderDependenciesWhoseConfigurationNeedsToChange[match]
            addLintViolation("this dependency should be moved to configuration $toConf", call)
                    .replaceWith(call, "$toConf '$match.module.id'")
        }
    }

    private Comparator<ResolvedDependency> dependencyComparator = new Comparator<ResolvedDependency>() {
        @Override
        int compare(ResolvedDependency d1, ResolvedDependency d2) {
            if (d1.moduleGroup != d2.moduleGroup)
                return d1?.moduleGroup?.compareTo(d2.moduleGroup) ?: d2.moduleGroup ? -1 : 1
            else if (d1.moduleName != d2.moduleName)
                return d1?.moduleName?.compareTo(d2.moduleName) ?: d2.moduleName ? -1 : 1
            else
                return new DefaultVersionComparator().asStringComparator().compare(d1.moduleVersion, d2.moduleVersion)
        }
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        fetchUnusedDependencyReportIfNecessary()

        if (call.methodAsString == 'dependencies') {
            // TODO match indentation of surroundings
            def indentation = ''.padLeft(call.columnNumber + 3)
            def transitiveSize = report.transitiveDependenciesToAddAsFirstOrder.size()

            if (transitiveSize == 1) {
                def d = report.transitiveDependenciesToAddAsFirstOrder.first()
                addLintViolation('one or more classes in your transitive dependencies are required by your code directly')
                        .insertAfter(call, "${indentation}compile '${d.module.id}'")
            } else if (transitiveSize > 1) {
                addLintViolation('one or more classes in your transitive dependencies are required by your code directly')
                        .insertAfter(call,
                        report.transitiveDependenciesToAddAsFirstOrder.toSorted(dependencyComparator).inject('') { deps, d ->
                            deps + "\n${indentation}compile '$d.module.id'"
                        }
                )
            }
        }
    }

    void fetchUnusedDependencyReportIfNecessary() {
        if (report == null) {
            report = UnusedDependencyReport.forProject(project)

            report.unevaluatedSourceSets.each {
                addLintViolation("the $it.compileConfigurationName configuration was not analyzed because there were no compiled classes found for source set $it.name",
                        GradleViolation.Level.Warning)
            }
        }
    }
}