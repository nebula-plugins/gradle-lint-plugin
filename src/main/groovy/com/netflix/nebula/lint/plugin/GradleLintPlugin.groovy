/*
 * Copyright 2015-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.nebula.lint.plugin

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.BuildCancelledException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.util.DeprecationLogger

class GradleLintPlugin implements Plugin<Project> {

    public static final String AUTO_LINT_GRADLE = 'autoLintGradle'

    @Override
    void apply(Project project) {

            failForKotlinScript(project)

            LintRuleRegistry.classLoader = getClass().classLoader
            def lintExt = project.extensions.create('gradleLint', GradleLintExtension)

            if (project.rootProject == project) {
                def autoLintTask = project.tasks.create(AUTO_LINT_GRADLE, LintGradleTask)
                autoLintTask.listeners = lintExt.listeners

                def manualLintTask = project.tasks.create('lintGradle', LintGradleTask)
                manualLintTask.group = 'lint'
                manualLintTask.failOnWarning = true

                def criticalLintTask = project.tasks.create('criticalLintGradle', LintGradleTask)
                criticalLintTask.group = 'lint'
                criticalLintTask.onlyCriticalRules = true

                def fixTask = project.tasks.create('fixGradleLint', FixGradleLintTask)
                fixTask.userDefinedListeners = lintExt.listeners

                def fixTask2 = project.tasks.create('fixLintGradle', FixGradleLintTask)
                fixTask2.userDefinedListeners = lintExt.listeners

                project.gradle.addListener(new LintListener() {
                    def allTasks

                    @Override
                    void graphPopulated(TaskExecutionGraph graph) {
                        allTasks = graph.allTasks
                    }

                    @Override
                    void buildFinished(BuildResult result) {
                        if (onlyIf()) {
                            autoLintTask.lint()
                        }
                    }

                    private boolean onlyIf() {
                        def shouldLint = project.hasProperty('gradleLint.alwaysRun') ?
                                Boolean.valueOf(project.property('gradleLint.alwaysRun').toString()) : lintExt.alwaysRun
                        def excludedAutoLintGradle = project.gradle.startParameter.excludedTaskNames.contains(AUTO_LINT_GRADLE)
                        def skipForSpecificTask = project.gradle.startParameter.taskNames.any { lintExt.skipForTasks.contains(it) }
                        def hasFailedTask = !lintExt.autoLintAfterFailure && allTasks.any { it.state.failure != null }
                        //when we already have failed critical lint task we don't want to run autolint
                        def hasFailedCriticalLintTask = allTasks.any { it == criticalLintTask && it.state.failure != null }
                        def hasExplicitLintTask = allTasks.any {
                            it == fixTask || it == fixTask2 || it == manualLintTask || it == autoLintTask
                        }
                        shouldLint && !excludedAutoLintGradle && !skipForSpecificTask && !hasFailedTask &&
                                !hasExplicitLintTask && !hasFailedCriticalLintTask
                    }
                })
            }

            configureReportTask(project, lintExt)

            project.plugins.withType(JavaBasePlugin) {
                project.tasks.withType(AbstractCompile) { task ->
                    project.rootProject.tasks.getByName('fixGradleLint').dependsOn(task)
                    project.rootProject.tasks.getByName('lintGradle').dependsOn(task)
                    project.rootProject.tasks.getByName('fixLintGradle').dependsOn(task)
                }
            }

    }

    def failForKotlinScript(Project project) {
        if (project.buildFile.name.toLowerCase().endsWith('.kts')) {
            throw new BuildCancelledException("Gradle Lint Plugin currently doesn't support kotlin build scripts." +
                    " Please, switch to groovy build script if you want to use linting.")
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

    private static abstract class LintListener extends BuildAdapter implements TaskExecutionGraphListener {}
}
