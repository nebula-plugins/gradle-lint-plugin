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

        if (project.rootProject == project) {
            def lintTask = project.tasks.create('lintGradle', GradleLintTask)
            lintTask.listeners = lintExt.listeners

            def fixTask = project.tasks.create('fixGradleLint', GradleLintCorrectionTask)
            fixTask.listeners = lintExt.listeners

            def fixTask2 = project.tasks.create('fixLintGradle', GradleLintCorrectionTask)
            fixTask2.listeners = lintExt.listeners
        } else {
//            project.rootProject.apply plugin: GradleLintPlugin
//            project.tasks.create('lintGradle').finalizedBy project.rootProject.tasks.getByName('lintGradle')
//            project.tasks.create('fixGradleLint').finalizedBy project.rootProject.tasks.getByName('fixGradleLint')
//            project.tasks.create('fixLintGradle').finalizedBy project.rootProject.tasks.getByName('fixGradleLint')
        }

        configureReportTask(project, lintExt)

        // ensure that lint runs
        project.tasks.whenTaskAdded { task ->
            def rootLint = project.rootProject.tasks.getByName('lintGradle')
            if (task != rootLint && !exemptTasks.contains(task.name)) {
                // when running a lint-eligible task on a subproject, we want to lint the whole project
                task.finalizedBy rootLint

                // because technically you can override path in a Gradle task implementation and cause path to be null!
                if(task.getPath() != null) {
                    try {
                        rootLint.shouldRunAfter task
                    } catch(Throwable t) {
                        // just quietly DON'T add rootLint to run after this task, it will probably still run because
                        // it will be hung on some other task as a shouldRunAfter
                    }
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
