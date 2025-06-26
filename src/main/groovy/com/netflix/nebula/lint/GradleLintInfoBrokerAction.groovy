package com.netflix.nebula.lint

import com.netflix.nebula.lint.plugin.ProjectInfo
import groovy.transform.Canonical
import org.gradle.api.Plugin
import org.gradle.api.Project

@Canonical
class GradleLintInfoBrokerAction extends GradleLintViolationAction {
    Plugin nebulaInfoBroker
    ProjectInfo projectInfo


    GradleLintInfoBrokerAction(Project project){
        this.projectInfo = ProjectInfo.from(project)
        project.getPlugins().withId('nebula.info-broker') { plugin ->
            nebulaInfoBroker = plugin
        }
    }

    @Override
    void lintFinished(Collection<GradleViolation> violations) {
        nebulaInfoBroker?.tap{
            def reportItems = violations.collect { buildReportItem(it) }
            it.addReport('gradleLintViolations', reportItems)
        }
    }

    @Override
    void lintFixesApplied(Collection<GradleViolation> violations) {
        nebulaInfoBroker?.tap {
            def reportItems = violations.findAll { !it.fixes.any { it.reasonForNotFixing } }
                    .collect { buildReportItem(it) }
            it.addReport('fixedGradleLintViolations', reportItems)
        }
    }

    LintReportItem buildReportItem(GradleViolation v) {
        def buildFilePath = projectInfo.rootDir.toURI().relativize(v.file.toURI()).toString()
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
