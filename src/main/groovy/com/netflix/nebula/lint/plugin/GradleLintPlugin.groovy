package com.netflix.nebula.lint.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class GradleLintPlugin implements Plugin<Project> {
    private final exemptTasks = ['help', 'tasks', 'dependencies', 'dependencyInsight',
        'components', 'model', 'projects', 'properties', 'fixGradleLint']

    @Override
    void apply(Project project) {
        LintRuleRegistry.classLoader = getClass().classLoader
        def lintExt = project.extensions.create('gradleLint', GradleLintExtension)

        // TODO
        // 1. Make gradleLint and fixGradleLint on root run against all subprojects
        // 2. Only automatically add root project's gradle lint the end of the build

        if (project.rootProject == project) {
            project.tasks.create('fixGradleLint', GradleLintCorrectionTask)
            project.tasks.create('gradleLint', GradleLintTask)
            project.rootProject.apply plugin: GradleLintPlugin
        } else {
            project.tasks.create('gradleLint') // this task does nothing
            project.tasks.create('fixGradleLint').finalizedBy project.rootProject.tasks.getByName('fixGradleLint')
        }

        configureReportTask(project, lintExt)

        // ensure that lint runs
        project.tasks.whenTaskAdded { task ->
            def rootLint = project.rootProject.tasks.getByName('gradleLint')
            if (task != rootLint && !exemptTasks.contains(task.name)) {
                // when running a lint-eligible task on a subproject, we want to lint the whole project
                task.finalizedBy rootLint

                // because technically you can override path in a Gradle task implementation and cause path to be null!
                if(task.getPath() != null) {
                    rootLint.shouldRunAfter task
                }
            }
        }
    }

    private void configureReportTask(Project project, GradleLintExtension extension) {
        def task = project.tasks.create('generateGradleLintReport', GradleLintReportTask)
        task.reports.all { report ->
            report.conventionMapping.with {
                enabled = { report.name == extension.reportFormat }
                destination = {
                    def fileSuffix = report.name == 'text' ? 'txt' : report.name
                    new File(project.buildDir, "reports/gradleLint/${project.name}.$fileSuffix")
                }
            }
        }
    }
}
