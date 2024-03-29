package com.netflix.nebula.lint.plugin

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task


abstract class AbstractLintPluginTaskConfigurer {
    public static final String LINT_GROUP = 'lint'
    public static final String AUTO_LINT_GRADLE = 'autoLintGradle'
    public static final String CLEAN_TASK_NAME = 'clean'
    public static final String LINT_GRADLE = 'lintGradle'
    public static final String CRITICAL_LINT_GRADLE = 'criticalLintGradle'
    public static final String FIX_GRADLE_LINT = 'fixGradleLint'
    public static final String FIX_LINT_GRADLE = 'fixLintGradle'
    public static final String GENERATE_GRADLE_LINT_REPORT = 'generateGradleLintReport'

    void configure(Project project) {
        def lintExt = project.extensions.create('gradleLint', GradleLintExtension)
        createTasks(project, lintExt)
        wireJavaPluginConditionally(project)
    }

    abstract void createTasks(Project project, GradleLintExtension lintExtension)

    protected void wireJavaPluginConditionally(Project project) {
        boolean wireJavaProject = project.hasProperty('gradleLint.wireJavaPlugin') ? Boolean.valueOf(project.property('gradleLint.wireJavaPlugin').toString()) : true
        if(wireJavaProject) {
            wireJavaPlugin(project)
        }
    }

    abstract void wireJavaPlugin(Project project)
    abstract Action<GradleLintReportTask> configureReportAction(Project project, GradleLintExtension extension)

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

    protected static String getReportFormat(Project project, GradleLintExtension extension) {
        return project.hasProperty('gradleLint.reportFormat') ? project.property('gradleLint.reportFormat') : extension.reportFormat
    }

    protected static boolean getReportOnlyFixableViolations(Project project, GradleLintExtension extension) {
        return project.hasProperty('gradleLint.reportOnlyFixableViolations') ? Boolean.valueOf(project.property('gradleLint.reportOnlyFixableViolations').toString()) : extension.reportOnlyFixableViolations
    }
}
