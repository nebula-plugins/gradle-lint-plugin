package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.analyzer.CorrectableStringSourceAnalyzer
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

    @TaskAction
    void lintCorrections() {
        def registry = new LintRuleRegistry(project)
        def ruleSet = RuleSetFactory.configureRuleSet(project.extensions
                .getByType(GradleLintExtension)
                .rules
                .collect { registry.findRule(it) }
                .flatten() as List<Rule>)

        def textOutput = textOutputFactory.create('lint')

        def analyzer = new CorrectableStringSourceAnalyzer(project.buildFile.text)
        def results = analyzer.analyze(ruleSet)

        def violations = results.violations
        def buildFilePath = relPath(project.rootDir, project.buildFile).path

        if(violations.isEmpty()) {
            textOutput.style(StyledTextOutput.Style.Identifier).println("Passed lint check with 0 violations; no corrections necessary")
            return
        }

        project.buildFile.newWriter().withWriter { w ->
            w << analyzer.corrected
        }

        def correctedViolations = 0, uncorrectedViolations = 0

        violations.eachWithIndex { Violation v, Integer i ->
            def severity = v.rule.priority <= 3 ? 'warning' : 'error'

            textOutput.withStyle(StyledTextOutput.Style.Failure).text(severity.padRight(10))
            textOutput.text(v.rule.name.padRight(25))
            textOutput.withStyle(StyledTextOutput.Style.Description).println(v.message)

            textOutput.withStyle(StyledTextOutput.Style.UserInput).println(buildFilePath + ':' + v.lineNumber)
            textOutput.println("$v.sourceLine")

            if(v instanceof GradleViolation) {
                def gv = v as GradleViolation
                if(gv.replacement) {
                    textOutput.withStyle(StyledTextOutput.Style.Identifier).println('\u2713 replaced with:')
                    textOutput.println(gv.replacement)
                    correctedViolations++
                } else if(gv.shouldDelete) {
                    textOutput.withStyle(StyledTextOutput.Style.Identifier).println('\u2713 deleted')
                    correctedViolations++
                } else {
                    textOutput.withStyle(StyledTextOutput.Style.Error).println('\u2716 no auto-correct available')
                    uncorrectedViolations++
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
