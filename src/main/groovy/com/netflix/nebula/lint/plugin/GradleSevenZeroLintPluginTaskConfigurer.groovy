package com.netflix.nebula.lint.plugin

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.AbstractCompile

class GradleSevenZeroLintPluginTaskConfigurer extends GradleLintPluginTaskConfigurer{
    @Override
    Action<GradleLintReportTask> configureReportAction(Project project, GradleLintExtension extension) {
        new Action<GradleLintReportTask>() {
            @Override
            void execute(GradleLintReportTask gradleLintReportTask) {
                gradleLintReportTask.reportOnlyFixableViolations = getReportOnlyFixableViolations(project, extension)
                gradleLintReportTask.reports.all { report ->
                    report.conventionMapping.with {
                        enabled = { report.name == getReportFormat(project, extension) }
                        destination = {
                            def fileSuffix = report.name == 'text' ? 'txt' : report.name
                            new File(project.buildDir, "reports/gradleLint/${project.name}.$fileSuffix")
                        }
                    }
                }
            }
        }
    }
}
