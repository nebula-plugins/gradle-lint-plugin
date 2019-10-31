package com.netflix.nebula.lint.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.TaskState

class GradleLintPluginTaskConfigurer extends AbstractLintPluginTaskConfigurer{

    @Override
    void createTasks(Project project, GradleLintExtension lintExt) {
        if (project.rootProject == project) {
            def autoLintTask = project.tasks.create(AUTO_LINT_GRADLE, LintGradleTask)
            autoLintTask.listeners = lintExt.listeners

            def manualLintTask = project.tasks.register('lintGradle', LintGradleTask) {
                group = 'lint'
                failOnWarning = true
            }


            def criticalLintTask = project.tasks.register('criticalLintGradle', LintGradleTask) {
                group = 'lint'
                onlyCriticalRules = true
            }


            def fixTask = project.tasks.register('fixGradleLint', FixGradleLintTask) {
                userDefinedListeners = lintExt.listeners
            }

            def fixTask2 = project.tasks.register('fixLintGradle', FixGradleLintTask) {
                userDefinedListeners = lintExt.listeners
            }

            List<TaskProvider> lintTasks = [fixTask, fixTask2, manualLintTask]

            configureAutoLint(autoLintTask, project, lintExt, lintTasks, criticalLintTask)

        }

        configureReportTask(project, lintExt)
    }

    private void configureAutoLint(Task autoLintTask, Project project, GradleLintExtension lintExt, List<TaskProvider> lintTasks, TaskProvider criticalLintTask) {
        List<TaskProvider> lintTasksToVerify = lintTasks + criticalLintTask
        project.afterEvaluate {
            if(lintExt.autoLintAfterFailure) {
                configureAutoLintWithFailures(autoLintTask, project, lintExt, lintTasksToVerify)
            } else {
                configureAutoLintWithoutFailures(autoLintTask, project, lintExt, lintTasksToVerify, criticalLintTask)
            }
        }
    }

    /**
     * finalizes all tasks with autoLint if the build doesn't have explicit lint task and has valid configuration
     * Hooks into failed tasks, too
     * @param autoLintTask
     * @param project
     * @param lintExt
     * @param lintTasksToVerify
     */
    private void configureAutoLintWithFailures(Task autoLintTask, Project project, GradleLintExtension lintExt, List<TaskProvider> lintTasksToVerify) {
        boolean hasExplicitLintTask = project.gradle.startParameter.taskNames.any { lintTasksToVerify.name.contains(it) }
        if(!hasValidTaskConfiguration(project, lintExt) || hasExplicitLintTask) {
            return
        }
        finalizeAllTasksWithAutoLint(project, lintTasksToVerify, autoLintTask)

    }

    /**
     * finalizes all tasks with autoLint if the build doesn't have explicit lint task and has valid configuration
     * Does not hook into failed tasks, too
     * @param autoLintTask
     * @param project
     * @param lintExt
     * @param lintTasks
     * @param criticalLintTask
     */
    private void configureAutoLintWithoutFailures(Task autoLintTask, Project project, GradleLintExtension lintExt, List<TaskProvider> lintTasks, TaskProvider criticalLintTask) {
        project.gradle.taskGraph.whenReady { taskGraph ->
            List<Task> allTasks = taskGraph.allTasks
            if (hasValidTaskConfiguration(project, lintExt)) {
                LinkedList tasks = taskGraph.executionPlan.executionQueue
                Task lastTask = tasks.last?.task
                taskGraph.addTaskExecutionListener(new TaskExecutionListener() {
                    @Override
                    void beforeExecute(Task task) {
                        //DO NOTHING
                    }

                    @Override
                    void afterExecute(Task task, TaskState taskState) {
                        if(hasExplicitLintTask(allTasks, lintTasks) || hasFailedCriticalLintTask(allTasks, criticalLintTask)) {
                            return
                        }
                        if(task.path == lastTask.path && !taskState.failure) {
                            autoLintTask.lint()
                        }
                    }
                })
            }
        }
    }

    /**
     * Finalizes all tasks that aren't lint related with autoLint
     * This works with --parallel and failed tasks
     * @param project
     * @param lintTasks
     * @param autoLintTask
     */
    private void finalizeAllTasksWithAutoLint(Project project, List<TaskProvider> lintTasks, Task autoLintTask) {
        project.tasks.configureEach { task ->
            if (!lintTasks.contains(task) && !task.name.contains(AUTO_LINT_GRADLE)) {
                task.finalizedBy autoLintTask
            }
        }
        project.childProjects.values().each { subProject ->
            finalizeAllTasksWithAutoLint(subProject, lintTasks, autoLintTask)
        }
    }

    private void configureReportTask(Project project, GradleLintExtension extension) {
        project.tasks.register('generateGradleLintReport', GradleLintReportTask) {
            reports.all { report ->
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
}
