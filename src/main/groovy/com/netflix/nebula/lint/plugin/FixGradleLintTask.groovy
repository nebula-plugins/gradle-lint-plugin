/*
 * Copyright 2015-2016 Netflix, Inc.
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
import com.netflix.nebula.lint.Styling
import org.eclipse.jgit.api.ApplyCommand
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

class FixGradleLintTask extends DefaultTask {
    List<GradleLintViolationAction> userDefinedListeners = []

    /**
     * Special listener tied into nebula.metrics via nebula.info to ship violation information to a
     * metrics endpoint
     */
    GradleLintInfoBrokerAction infoBrokerAction = new GradleLintInfoBrokerAction(project)

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        null // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    }

    @TaskAction
    void lintCorrections() {
        // look at org.gradle.logging.internal.DefaultColorMap
        def violations = new LintService().lint(project).violations

        (userDefinedListeners + infoBrokerAction + new GradleLintPatchAction(project)).each {
            it.lintFinished(violations)
        }

        def patchFile = new File(project.buildDir, GradleLintPatchAction.PATCH_NAME)
        if(patchFile.exists()) {
            new ApplyCommand(new NotNecessarilyGitRepository(project.projectDir)).setPatch(patchFile.newInputStream()).call()
        }

        (userDefinedListeners + infoBrokerAction + consoleOutputAction).each {
            it.lintFixesApplied(violations)
        }
    }

    final def consoleOutputAction = new GradleLintViolationAction() {

        StyledTextOutput textOutput = textOutputFactory.create('lint')

        @Override
        void lintFixesApplied(Collection<GradleViolation> violations) {
            if(violations.empty) {
                textOutput.style(StyledTextOutput.Style.Identifier).println("Passed lint check with 0 violations; no corrections necessary")
            } else {
                textOutput.withStyle(StyledTextOutput.Style.UserInput).text('\nThis project contains lint violations. ')
                textOutput.println('A complete listing of my attempt to fix them follows. Please review and commit the changes.\n')
            }

            def completelyFixed = 0

            violations.groupBy { it.file }.each { buildFile, projectViolations ->
                def buildFilePath = project.rootDir.toURI().relativize(buildFile.toURI()).toString()

                projectViolations.each { v ->
                    def unfixed = v.fixes.findAll { it.reasonForNotFixing != null }
                    if(v.fixes.isEmpty()) {
                        textOutput.withStyle(Styling.Yellow).text('nothing to do'.padRight(15))
                    }
                    else if(unfixed.isEmpty()) {
                        textOutput.withStyle(Styling.Green).text('fixed'.padRight(15))
                        completelyFixed++
                    }
                    else if(unfixed.size() == v.fixes.size()) {
                        textOutput.withStyle(Styling.Yellow).text('unfixed'.padRight(15))
                    }
                    else {
                        textOutput.withStyle(Styling.Yellow).text('semi-fixed'.padRight(15))
                    }

                    textOutput.text(v.rule.ruleId.padRight(35))
                    textOutput.withStyle(Styling.Yellow).println(v.message)

                    if(v.lineNumber)
                        textOutput.withStyle(Styling.Bold).println(buildFilePath + ':' + v.lineNumber)
                    if (v.sourceLine)
                        textOutput.println(v.sourceLine)

                    if(!unfixed.isEmpty()) {
                        textOutput.withStyle(Styling.Bold).println('reason not fixed: ')
                        unfixed.collect { it.reasonForNotFixing }.unique().each { textOutput.println(it.message) }
                    }

                    textOutput.println() // extra space between violations
                }
            }

            textOutput.style(Styling.Green).println("Corrected $completelyFixed lint problems\n")
        }
    }
}
