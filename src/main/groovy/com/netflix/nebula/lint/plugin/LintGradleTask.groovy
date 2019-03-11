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

import com.netflix.nebula.lint.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.util.DeprecationLogger

import static com.netflix.nebula.lint.StyledTextService.Styling.*

class LintGradleTask extends DefaultTask {
    List<GradleLintViolationAction> listeners = []

    boolean failOnWarning = false
    boolean onlyCriticalRules = false

    LintGradleTask() {
        group = 'lint'
    }

    @TaskAction
    void lint() {
        def violations = new LintService().lint(project, onlyCriticalRules).violations
                .unique { v1, v2 -> v1.is(v2) ? 0 : 1 }

        (listeners + new GradleLintPatchAction(project) + new GradleLintInfoBrokerAction(project) + consoleOutputAction).each {
            it.lintFinished(violations)
        }

    }

    final def consoleOutputAction = new GradleLintViolationAction() {
        @Override
        void lintFinished(Collection<GradleViolation> violations) {
            int errors = violations.count { it.rule.priority == 1 }
            int warnings = violations.count { it.rule.priority != 1 }

            def textOutput = new StyledTextService(getServices())

            if (!violations.empty) {
                textOutput.withStyle(Bold).text('\nThis project contains lint violations. ')
                textOutput.println('A complete listing of the violations follows. ')

                if (errors) {
                    textOutput.text('Because some were serious, the overall build status has been changed to ')
                            .withStyle(Red).println("FAILED\n")
                } else {
                    textOutput.println('Because none were serious, the build\'s overall status was unaffected.\n')
                }
            }

            violations.groupBy { it.file }.each { buildFile, violationsByFile ->

                violationsByFile.each { v ->
                    String buildFilePath = project.rootDir.toURI().relativize(v.file.toURI()).toString()
                    if (v.rule.priority == 1) {
                        textOutput.withStyle(Red).text('error'.padRight(10))
                    } else {
                        textOutput.withStyle(Red).text('warning'.padRight(10))
                    }

                    textOutput.text(v.rule.name.padRight(35))

                    textOutput.withStyle(Yellow).text(v.message)
                    if (v.fixes.empty) {
                        textOutput.withStyle(Yellow).text(' (no auto-fix available)')
                    }
                    textOutput.println()

                    if (v.lineNumber) {
                        textOutput.withStyle(Bold).println(buildFilePath + ':' + v.lineNumber)
                    }
                    if (v.sourceLine) {
                        textOutput.println("$v.sourceLine")
                    }

                    textOutput.println() // extra space between violations
                }
            }

            if (!violations.empty) {
                textOutput.withStyle(Red).println("\u2716 ${errors + warnings} problem${errors + warnings == 1 ? '' : 's'} ($errors error${errors == 1 ? '' : 's'}, $warnings warning${warnings == 1 ? '' : 's'})\n".toString())
                textOutput.text("To apply fixes automatically, run ").withStyle(Bold).text("fixGradleLint")
                textOutput.println(", review, and commit the changes.\n")

                if (errors > 0) {
                    throw new GradleException("This build contains $errors critical lint violation${errors == 1 ? '' : 's'}")
                }

                if (failOnWarning) {
                    throw new GradleException("This build contains $warnings lint violation${warnings == 1 ? '' : 's'}")
                }
            }
        }
    }
}
