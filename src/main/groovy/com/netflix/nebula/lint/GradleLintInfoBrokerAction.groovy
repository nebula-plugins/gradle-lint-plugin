package com.netflix.nebula.lint

import groovy.transform.Canonical
import org.gradle.api.Project

@Canonical
class GradleLintInfoBrokerAction extends GradleLintViolationAction {
    Project project

    @Override
    void lintFinished(Collection<GradleViolation> violations) {
        project.getPlugins().withId('nebula.info-broker') {
            def reportItems = violations.collect { buildReportItem(it) }
            it.addReport('gradleLintViolations', reportItems)
        }
    }

    @Override
    void lintFixesApplied(Collection<GradleViolation> violations) {
        project.getPlugins().withId('nebula.info-broker') {
            def reportItems = violations.findAll { !it.fixes.any { it.reasonForNotFixing } }
                    .collect { buildReportItem(it) }
            it.addReport('fixedGradleLintViolations', reportItems)
        }
    }

    LintReportItem buildReportItem(GradleViolation v) {
        def buildFilePath = project.rootDir.toURI().relativize(v.file.toURI()).toString()
        new LintReportItem(buildFilePath, v.rule.name, v.rule.getPriority() as String,
                v.lineNumber ?: -1, v.sourceLine ?: 'unspecified', v.message ?: "")
    }
}

@Canonical
class LintReportItem {
    String buildFilePath
    String ruleId
    String severity
    Integer lineNumber
    String sourceLine
    String message
}
