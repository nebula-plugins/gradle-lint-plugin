package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.analyzer.CorrectableStringSourceAnalyzer
import com.netflix.nebula.lint.postprocess.EmptyClosureRule
import com.netflix.nebula.lint.rule.GradleViolation
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

class GradleLintCorrectionTask extends DefaultTask {
    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        null // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    }

    /**
     * Clean up any ugly artifacts we may have created through the auto-fix process
     * (e.g. empty extension object closures that have had all their property definitions removed)
     */
    void postProcess() {
        def ruleSet = RuleSetFactory.configureRuleSet([new EmptyClosureRule()])

        def analyzer = new CorrectableStringSourceAnalyzer(project.buildFile.text)
        analyzer.analyze(ruleSet)

        project.buildFile.newWriter().withWriter { w ->
            w << analyzer.corrected
        }
    }

    @TaskAction
    void lintCorrections() {
        if(!project.buildFile.exists()) {
            return
        }

        def registry = new LintRuleRegistry(project)
        def ruleSet = RuleSetFactory.configureRuleSet(project.extensions
                .getByType(GradleLintExtension)
                .rules
                .collect { registry.buildRules(it) }
                .flatten() as List<Rule>)

        // look at org.gradle.logging.internal.DefaultColorMap
        def textOutput = textOutputFactory.create('lint')

        def analyzer = new CorrectableStringSourceAnalyzer(project.buildFile.text)
        def results = analyzer.analyze(ruleSet)

        if(results.violations.isEmpty()) {
            textOutput.style(StyledTextOutput.Style.Identifier).println("Passed lint check with 0 violations; no corrections necessary")
            return
        }

        // perform initial correction
        project.buildFile.newWriter().withWriter { w ->
            w << analyzer.corrected
        }
        postProcess()

        printReport(results.violations, textOutput)
    }

    private List printReport(List<Violation> violations, textOutput) {
        def correctedViolations = 0, uncorrectedViolations = 0
        def buildFilePath = relPath(project.rootDir, project.buildFile).path

        violations.eachWithIndex { v, i ->
            def severity = v.rule.priority <= 3 ? 'warning' : 'error'

            if (v instanceof GradleViolation && v.isFixable()) {
                textOutput.withStyle(StyledTextOutput.Style.Identifier).text('fixed'.padRight(10))
            } else {
                textOutput.withStyle(StyledTextOutput.Style.Failure).text(severity.padRight(10))
            }

            textOutput.text(v.rule.ruleId.padRight(35))
            textOutput.withStyle(StyledTextOutput.Style.Description).println(v.message)

            textOutput.withStyle(StyledTextOutput.Style.UserInput).println(buildFilePath + ':' + v.lineNumber)
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
                    textOutput.print(v.addition)
                    correctedViolations++
                }
            } else {
                textOutput.withStyle(StyledTextOutput.Style.Error).println('\u2716 no auto-correct available')
                uncorrectedViolations++
            }

            textOutput.println() // extra space between violations
        }

        if(correctedViolations > 0)
            textOutput.style(StyledTextOutput.Style.Identifier).println("Corrected $correctedViolations lint problems")

        if(uncorrectedViolations > 0)
            textOutput.style(StyledTextOutput.Style.Error).println("Corrected $correctedViolations lint problems")
    }

    private static File relPath(File root, File f) {
        new File(root.toURI().relativize(f.toURI()).toString())
    }
}
