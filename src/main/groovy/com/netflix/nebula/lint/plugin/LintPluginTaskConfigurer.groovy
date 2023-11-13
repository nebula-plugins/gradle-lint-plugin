package com.netflix.nebula.lint.plugin

import org.gradle.api.Action
import org.gradle.api.Project

class LintPluginTaskConfigurer extends GradleLintPluginTaskConfigurer {
    @Override
    Action<GradleLintReportTask> configureReportAction(Project project, GradleLintExtension extension) {
        new Action<GradleLintReportTask>() {
            @Override
            void execute(GradleLintReportTask gradleLintReportTask) {
                gradleLintReportTask.reportOnlyFixableViolations.set(getReportOnlyFixableViolations(project, extension))
                gradleLintReportTask.reports.all { report ->
                    def fileSuffix = report.name == 'text' ? 'txt' : report.name
                    report.conventionMapping.with {
                        required.set(report.name == getReportFormat(project, extension))
                        outputLocation.set(project.layout.buildDirectory.file("reports/gradleLint/${project.name}.$fileSuffix"))
                    }
                }
            }
        }
    }
}
