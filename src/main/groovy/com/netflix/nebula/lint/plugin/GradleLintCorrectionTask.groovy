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

import com.netflix.nebula.lint.analyzer.CorrectableStringSourceAnalyzer
import com.netflix.nebula.lint.postprocess.EmptyClosureRule
import com.netflix.nebula.lint.rule.GradleViolation
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

class GradleLintCorrectionTask extends DefaultTask {
    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        null // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    }

    void performCorrections(Project p, CorrectableStringSourceAnalyzer analyzer) {
        p.buildFile.text = analyzer.corrected // perform initial correction

        // Clean up any ugly artifacts we may have created through the auto-fix process
        // (e.g. empty extension object closures that have had all their property definitions removed)
        def ruleSet = RuleSetFactory.configureRuleSet([new EmptyClosureRule()])
        def postProcessor = new CorrectableStringSourceAnalyzer(p.buildFile.text)
        postProcessor.analyze(ruleSet)

        p.buildFile.text = postProcessor.corrected
    }

    @TaskAction
    void lintCorrections() {
        // look at org.gradle.logging.internal.DefaultColorMap
        def textOutput = textOutputFactory.create('lint')
        def registry = new LintRuleRegistry()

        def violationsByProject = [:]

        ([project] + project.subprojects).each { p ->
            if (p.buildFile.exists()) {
                def extension
                try {
                    extension = p.extensions.getByType(GradleLintExtension)
                } catch(UnknownDomainObjectException) {
                    // if the subproject has not applied lint, use the extension configuration from the root project
                    extension = p.rootProject.extensions.getByType(GradleLintExtension)
                }

                def ruleSet = RuleSetFactory.configureRuleSet(extension.rules.collect { registry.buildRules(it, p) }
                        .flatten() as List<Rule>)

                def analyzer = new CorrectableStringSourceAnalyzer(p.buildFile.text)
                def results = analyzer.analyze(ruleSet)

                performCorrections(p, analyzer)
                violationsByProject[p] = results.violations
            }
        }

        def allViolations = violationsByProject.values().flatten()
        def correctedViolations = 0, uncorrectedViolations = 0

        if(allViolations.isEmpty()) {
            textOutput.style(StyledTextOutput.Style.Identifier).println("Passed lint check with 0 violations; no corrections necessary")
        } else {
            textOutput.withStyle(StyledTextOutput.Style.UserInput).text('\nThis project contains lint violations. ')
            textOutput.println('A complete listing of my attempt to fix them follows. Please review and commit the changes.\n')

            violationsByProject.entrySet().each {
                def buildFilePath = it.key.rootDir.toURI().relativize(it.key.buildFile.toURI()).toString()
                def violations = it.value

                violations.each { Violation v ->
                    def severity = v.rule.priority <= 3 ? 'warning' : 'error'

                    if (v instanceof GradleViolation && v.isFixable()) {
                        textOutput.withStyle(StyledTextOutput.Style.Identifier).text('fixed'.padRight(10))
                    } else {
                        textOutput.withStyle(StyledTextOutput.Style.Failure).text(severity.padRight(10))
                    }

                    textOutput.text(v.rule.ruleId.padRight(35))
                    textOutput.withStyle(StyledTextOutput.Style.Description).println(v.message)

                    if(v.lineNumber)
                        textOutput.withStyle(StyledTextOutput.Style.UserInput).println(buildFilePath + ':' + v.lineNumber)
                    if(v.sourceLine)
                        textOutput.println("$v.sourceLine")

                    if (v instanceof GradleViolation && v.isFixable()) {
                        if (v.replacement) {
                            textOutput.withStyle(StyledTextOutput.Style.UserInput).println('replaced with:')
                            textOutput.println(v.replacement)
                            correctedViolations++
                        } else if (v.deleteLine) {
                            textOutput.withStyle(StyledTextOutput.Style.UserInput).println("deleted line $v.deleteLine")
                            correctedViolations++
                        } else if (v.addition) {
                            textOutput.withStyle(StyledTextOutput.Style.UserInput).println("adding:")
                            textOutput.println(v.addition.stripIndent().trim())
                            correctedViolations++
                        }
                    } else {
                        textOutput.withStyle(StyledTextOutput.Style.Error).println('\u2716 no auto-correct available')
                        uncorrectedViolations++
                    }

                    textOutput.println() // extra space between violations
                }
            }

            if(correctedViolations > 0)
                textOutput.style(StyledTextOutput.Style.Identifier).println("Corrected $correctedViolations lint problems\n")

            if(uncorrectedViolations > 0)
                textOutput.style(StyledTextOutput.Style.Error).println("Corrected $correctedViolations lint problems\n")
        }
    }
}
