package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator

abstract class AbstractDependencyReportRule extends GradleLintRule implements GradleModelAware {
    DependencyReport report

    @Override
    protected final void beforeApplyTo() {
        if (report == null) {
            report = DependencyReport.forRule(this)
        }
    }

    protected Comparator<ResolvedDependency> dependencyComparator = new Comparator<ResolvedDependency>() {
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
}
