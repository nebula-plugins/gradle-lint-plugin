/*
 * Copyright 2015-2017 Netflix, Inc.
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.compile.AbstractCompile

class GradleLintPlugin implements Plugin<Project> {
    private final exemptTasks = ['help', 'tasks', 'dependencies', 'dependencyInsight',
        'components', 'model', 'projects', 'properties', 'wrapper', 'lintGradle', 'fixGradleLint', 'fixLintGradle']

    @Override
    void apply(Project project) {
        LintRuleRegistry.classLoader = getClass().classLoader
        def lintExt = project.extensions.create('gradleLint', GradleLintExtension)

        if (project.rootProject == project) {
            def autoLintTask = project.tasks.create('autoLintGradle', LintGradleTask)
            autoLintTask.listeners = lintExt.listeners

            def manualLintTask = project.tasks.create('lintGradle', LintGradleTask)
            manualLintTask.group = 'lint'
            manualLintTask.failOnWarning = true

            def fixTask = project.tasks.create('fixGradleLint', FixGradleLintTask)
            fixTask.userDefinedListeners = lintExt.listeners

            def fixTask2 = project.tasks.create('fixLintGradle', FixGradleLintTask)
            fixTask2.userDefinedListeners = lintExt.listeners

            autoLintTask.onlyIf {
                def allTasks = project.gradle.taskGraph.allTasks
                def hasFailedTask = !lintExt.autoLintAfterFailure && allTasks.any { it.state.failure != null }
                def hasExplicitLintTask = allTasks.any { it == fixTask || it == fixTask2 || it == manualLintTask }
                !hasFailedTask && !hasExplicitLintTask
            }
        }

        configureReportTask(project, lintExt)

        def finalizeByLint = { task ->
            if(lintExt.alwaysRun) {
                def rootLint = project.rootProject.tasks.getByName('autoLintGradle')
                if (task != rootLint && !exemptTasks.contains(task.name)) {
                    // when running a lint-eligible task on a subproject, we want to lint the whole project
                    task.finalizedBy rootLint

                    // because technically you can override path in a Gradle task implementation and cause path to be null!
                    if (task.getPath() != null) {
                        try {
                            rootLint.shouldRunAfter task
                        } catch (Throwable ignored) {
                            // just quietly DON'T add rootLint to run after this task, it will probably still run because
                            // it will be hung on some other task as a shouldRunAfter
                        }
                    }
                }
            }
        }

        // ensure that lint runs
        project.afterEvaluate {
            project.tasks.each { finalizeByLint(it) }
        }

        project.plugins.withType(JavaBasePlugin) {
            project.tasks.withType(AbstractCompile) { task ->
                project.rootProject.tasks.getByName('fixGradleLint').dependsOn(task)
                project.rootProject.tasks.getByName('lintGradle').dependsOn(task)
                project.rootProject.tasks.getByName('fixLintGradle').dependsOn(task)
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
