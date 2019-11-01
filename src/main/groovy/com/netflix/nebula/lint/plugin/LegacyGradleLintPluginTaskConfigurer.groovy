package com.netflix.nebula.lint.plugin

import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.compile.AbstractCompile

/**
 * Configure gradle lint tasks for gradle versions older than 5.
 */
class LegacyGradleLintPluginTaskConfigurer extends AbstractLintPluginTaskConfigurer {

    @Override
    void createTasks(Project project, GradleLintExtension lintExt) {
        if (project.rootProject == project) {
            def autoLintTask = project.tasks.create(AUTO_LINT_GRADLE, LintGradleTask)
            autoLintTask.listeners = lintExt.listeners

            def manualLintTask = project.tasks.create(LINT_GRADLE, LintGradleTask)
            manualLintTask.group = LINT_GROUP
            manualLintTask.failOnWarning = true

            def criticalLintTask = project.tasks.create(CRITICAL_LINT_GRADLE, LintGradleTask)
            criticalLintTask.group = LINT_GROUP
            criticalLintTask.onlyCriticalRules = true

            def fixTask = project.tasks.create(FIX_GRADLE_LINT, FixGradleLintTask)
            fixTask.userDefinedListeners = lintExt.listeners

            def fixTask2 = project.tasks.create(FIX_LINT_GRADLE, FixGradleLintTask)
            fixTask2.userDefinedListeners = lintExt.listeners

            List<Task> lintTasks = [fixTask, fixTask2, manualLintTask, autoLintTask]

            configureLegacyAutoLint(autoLintTask, project, lintExt, lintTasks, criticalLintTask)

        }

        configureReportTask(project, lintExt)
    }


    @Override
    void wireJavaPlugin(Project project) {
        project.plugins.withType(JavaBasePlugin) {
            project.tasks.withType(AbstractCompile) { task ->
                project.rootProject.tasks.getByName(FIX_GRADLE_LINT).dependsOn(task)
                project.rootProject.tasks.getByName(LINT_GRADLE).dependsOn(task)
                project.rootProject.tasks.getByName(FIX_LINT_GRADLE).dependsOn(task)
            }
        }
    }

    /**
     * Configures autoLint for build in old versions of Gradle
     * This approach is not valid on new Gradle versions since lint can do configuration resolution and doing this in BuildListener is now considered un-managed thread and bad practice
     * @param autoLintTask
     * @param project
     * @param lintExt
     * @param lintTasks
     * @param criticalLintTask
     */
    private void configureLegacyAutoLint(LintGradleTask autoLintTask, Project project, GradleLintExtension lintExt, List<Task> lintTasks, Task criticalLintTask) {
        project.gradle.addListener(new GradleLintPlugin.LintListener() {
            List<Task> allTasks

            @Override
            void graphPopulated(TaskExecutionGraph graph) {
                allTasks = graph.allTasks
            }

            @Override
            void buildFinished(BuildResult result) {
                def hasFailedTask = !lintExt.autoLintAfterFailure && allTasks.any { it.state.failure != null }
                if(hasFailedTask || !hasValidTaskConfiguration(project, lintExt) ||
                        hasExplicitLintTask(allTasks, lintTasks) ||
                        hasFailedCriticalLintTask(allTasks, criticalLintTask)) {
                    return
                }
                autoLintTask.lint()
            }
        })
    }

    private void configureReportTask(Project project, GradleLintExtension extension) {
        def task = project.tasks.create(GENERATE_GRADLE_LINT_REPORT, GradleLintReportTask)
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
