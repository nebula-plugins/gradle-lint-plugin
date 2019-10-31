package com.netflix.nebula.lint.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.compile.AbstractCompile

abstract class AbstractLintPluginTaskConfigurer {
    public static final String AUTO_LINT_GRADLE = 'autoLintGradle'

    void configure(Project project) {
        def lintExt = project.extensions.create('gradleLint', GradleLintExtension)
        createTasks(project, lintExt)
        wireJavaPlugin(project)
    }

    abstract void createTasks(Project project, GradleLintExtension lintExtension)

    protected void wireJavaPlugin(Project project) {
        project.plugins.withType(JavaBasePlugin) {
            project.tasks.withType(AbstractCompile) { task ->
                project.rootProject.tasks.getByName('fixGradleLint').dependsOn(task)
                project.rootProject.tasks.getByName('lintGradle').dependsOn(task)
                project.rootProject.tasks.getByName('fixLintGradle').dependsOn(task)
            }
        }
    }

    protected static boolean hasValidTaskConfiguration(Project project, GradleLintExtension lintExt) {
        boolean shouldLint = project.hasProperty('gradleLint.alwaysRun') ?
                Boolean.valueOf(project.property('gradleLint.alwaysRun').toString()) : lintExt.alwaysRun
        boolean excludedAutoLintGradle = project.gradle.startParameter.excludedTaskNames.contains(AUTO_LINT_GRADLE)
        boolean skipForSpecificTask = project.gradle.startParameter.taskNames.any { lintExt.skipForTasks.contains(it) }
        return shouldLint && !excludedAutoLintGradle && !skipForSpecificTask
    }

    protected static boolean hasExplicitLintTask(List<Task> allTasks, def lintTasks) {
        allTasks.any {
            lintTasks.contains(it)
        }
    }

    protected static boolean hasFailedCriticalLintTask(List<Task> tasks, def criticalLintTask) {
        return tasks.any { it == criticalLintTask && it.state.failure != null }
    }
}
