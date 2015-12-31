package com.netflix.nebula.lint.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class GradleLintPlugin implements Plugin<Project> {
    private final exemptTasks = ['help', 'tasks', 'dependencies', 'dependencyInsight',
        'components', 'model', 'projects', 'properties']

    @Override
    void apply(Project project) {
        def lintExt = project.extensions.create('lint', GradleLintExtension)
        project.tasks.create('fixBuildLint', GradleLintCorrectionTask)
        def lint = project.tasks.create('buildLint', GradleLintTask)
        configureReportTask(project, lintExt)

        project.rootProject.apply plugin: GradleLintPlugin
        def rootLint = project.rootProject.tasks.getByName('buildLint')

        // ensure that lint runs
        project.tasks.whenTaskAdded { task ->
            if(task != lint && !exemptTasks.contains(task.name)) {
                task.finalizedBy lint
                lint.shouldRunAfter task

                // when running a lint-eligible task on a subproject, we want to lint the root project as well
                task.finalizedBy rootLint
                rootLint.shouldRunAfter task
            }
        }
    }

    private void configureReportTask(Project project, GradleLintExtension extension) {
        def task = project.tasks.create('generateBuildLintReport', GradleLintReportTask)
        task.reports.all { report ->
            report.conventionMapping.with {
                enabled = { report.name == extension.reportFormat }
                destination = {
                    def fileSuffix = report.name == 'text' ? 'txt' : report.name
                    new File(project.buildDir, "reports/lint/${project.name}.$fileSuffix")
                }
            }
        }
    }
}
