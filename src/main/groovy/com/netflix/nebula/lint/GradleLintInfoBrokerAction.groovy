package com.netflix.nebula.lint

import groovy.transform.Canonical
import org.gradle.api.Project

@Canonical
class GradleLintInfoBrokerAction extends GradleLintViolationAction {
    Project project

    @Override
    void lintFinished(Collection<GradleViolation> violations) {
        violations.collect { v ->
            def buildFilePath = project.rootDir.toURI().relativize(v.buildFile.toURI()).toString()
            new LintViolationReportItem(buildFilePath, v.rule.ruleId as String, v.level.toString(),
                    v.lineNumber ?: -1, v.sourceLine ?: 'unspecified')
        }
    }
}

@Canonical
class LintViolationReportItem {
    String buildFilePath
    String ruleId
    String severity
    Integer lineNumber
    String sourceLine
}
