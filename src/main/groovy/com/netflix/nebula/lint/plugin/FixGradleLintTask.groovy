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

import com.netflix.nebula.lint.GradleLintInfoBrokerAction
import com.netflix.nebula.lint.GradleLintPatchAction
import com.netflix.nebula.lint.GradleLintViolationAction
import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.StyledTextService

import org.eclipse.jgit.api.ApplyCommand
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.internal.deprecation.DeprecationLogger

import static com.netflix.nebula.lint.StyledTextService.Styling.*

abstract class FixGradleLintTask extends DefaultTask implements VerificationTask {
    @Input
    @Optional
    abstract ListProperty<GradleLintViolationAction> getUserDefinedListeners()

    /**
     * Special listener tied into nebula.metrics via nebula.info to ship violation information to a
     * metrics endpoint
     */
    @Internal
    GradleLintInfoBrokerAction infoBrokerAction

    @Internal
    abstract Property<ProjectInfo> getProjectInfo()

    @Internal
    GradleLintPatchAction patchAction

    @Internal
    abstract Property<ProjectTree> getProjectTree()



    FixGradleLintTask() {
        projectInfo.set(project.provider { ProjectInfo.from(project) })
        projectTree.set(project.provider {ProjectTree.from(project) })
        infoBrokerAction = new GradleLintInfoBrokerAction(project)
        patchAction = new GradleLintPatchAction(projectInfo.get())
        userDefinedListeners.convention([])
        outputs.upToDateWhen { false }
        group = 'lint'
        try {
            def method = Task.getMethod("notCompatibleWithConfigurationCache")
            method.invoke(this)
        } catch (NoSuchMethodException ignore) {
        }
    }

    @TaskAction
    void lintCorrections() {
        DeprecationLogger.whileDisabled {
            def violations = new LintService().lint(projectTree.get(), false).violations
                    .unique { v1, v2 -> v1.is(v2) ? 0 : 1 }

            (userDefinedListeners.get() + infoBrokerAction + patchAction).each {
                it.lintFinished(violations)
            }

            def patchFile = new File(projectInfo.get().buildDirectory, GradleLintPatchAction.PATCH_NAME)
            if (patchFile.exists()) {
                new ApplyCommand(new NotNecessarilyGitRepository(projectInfo.get().projectDir)).setPatch(patchFile.newInputStream()).call()
            }

            (userDefinedListeners.get() + infoBrokerAction + consoleOutputAction()).each {
                it.lintFixesApplied(violations)
            }
        }


    }

    GradleLintViolationAction consoleOutputAction() {
        new GradleLintViolationAction() {
            StyledTextService textOutput = new StyledTextService(getServices())

            @Override
            void lintFixesApplied(Collection<GradleViolation> violations) {
                if (violations.empty) {
                    textOutput.withStyle(Green).println("Passed lint check with 0 violations; no corrections necessary")
                } else {
                    textOutput.withStyle(Bold).text('\nThis project contains lint violations. ')
                    textOutput.println('A complete listing of my attempt to fix them follows. Please review and commit the changes.\n')
                }

                int completelyFixed = 0
                int unfixedCriticalViolations = 0

                violations.groupBy { it.file }.each { buildFile, projectViolations ->

                    projectViolations.each { v ->
                        String buildFilePath = project.rootDir.toURI().relativize(v.file.toURI()).toString()
                        def unfixed = v.fixes.findAll { it.reasonForNotFixing != null }
                        if (v.fixes.empty) {
                            textOutput.withStyle(Yellow).text('needs fixing'.padRight(15))
                            if (v.rule.priority == 1) {
                                unfixedCriticalViolations++
                            }
                        } else if (unfixed.empty) {
                            textOutput.withStyle(Green).text('fixed'.padRight(15))
                            completelyFixed++
                        } else if (unfixed.size() == v.fixes.size()) {
                            textOutput.withStyle(Yellow).text('unfixed'.padRight(15))
                            if (v.rule.priority == 1) {
                                unfixedCriticalViolations++
                            }
                        } else {
                            textOutput.withStyle(Yellow).text('semi-fixed'.padRight(15))
                            if (v.rule.priority == 1) {
                                unfixedCriticalViolations++
                            }
                        }

                        textOutput.text(v.rule.name.padRight(35))
                        textOutput.withStyle(Yellow).println(v.message)

                        if (v.lineNumber) {
                            textOutput.withStyle(Bold).println(buildFilePath + ':' + v.lineNumber)
                        }
                        if (v.sourceLine) {
                            textOutput.println(v.sourceLine)
                        }

                        if (!unfixed.empty) {
                            textOutput.withStyle(Bold).println('reason not fixed: ')
                            unfixed.collect { it.reasonForNotFixing }.unique().each { textOutput.println(it.message) }
                        }

                        textOutput.println() // extra space between violations
                    }
                }

                textOutput.withStyle(Green).println("Corrected $completelyFixed lint problems\n")

                if (unfixedCriticalViolations > 0) {
                    throw new GradleException("This build contains $unfixedCriticalViolations critical lint violation" +
                            "${unfixedCriticalViolations == 1 ? '' : 's'} that could not be automatically fixed")
                }
            }
        }
    }
}
