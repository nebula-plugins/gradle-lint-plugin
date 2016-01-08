package com.netflix.nebula.lint.plugin

import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

class GradleLintTask extends DefaultTask {
    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        null // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    }

    @TaskAction
    void lint() {
        def textOutput = textOutputFactory.create('lint')
        def buildFilePath = relPath(project.rootDir, project.buildFile).path

        def registry = new LintRuleRegistry(project)
        def ruleSet = RuleSetFactory.configureRuleSet(project
                .extensions
                .getByType(GradleLintExtension)
                .rules
                .collect { registry.buildRules(it) }
                .flatten() as List<Rule>)

        def violations = new StringSourceAnalyzer(project.buildFile.text).analyze(ruleSet).violations
        def totalBySeverity = violations.countBy { it.rule.priority <= 3 ? 'warning' : 'error' }

        if (!violations.isEmpty()) {
            textOutput.withStyle(StyledTextOutput.Style.UserInput).text('\nThis build contains lint violations. ')
            textOutput.println('A complete listing of the violations follows. ')

            if (totalBySeverity.error) {
                textOutput.text('Because some were serious, the overall build status has been changed to ')
                        .withStyle(StyledTextOutput.Style.Failure).println("FAILED\n")
            } else {
                textOutput.println('Because none were serious, the build\'s overall status was unaffected.\n')
            }
        }

        violations.eachWithIndex { Violation v, Integer i ->
            def severity = v.rule.priority <= 3 ? 'warning' : 'error'

            textOutput.withStyle(StyledTextOutput.Style.Failure).text(severity.padRight(10))
            textOutput.text(v.rule.ruleId.padRight(25))
            textOutput.withStyle(StyledTextOutput.Style.Description).println(v.message)

            textOutput.withStyle(StyledTextOutput.Style.UserInput).println(buildFilePath + ':' + v.lineNumber)
            textOutput.println("$v.sourceLine\n") // extra space between violations
        }

        if (!violations.isEmpty()) {
            textOutput.withStyle(StyledTextOutput.Style.Failure)
                    .println("\u2716 ${buildFilePath}: ${violations.size()} problem${violations.isEmpty() ? '' : 's'} (${totalBySeverity.error ?: 0} errors, ${totalBySeverity.warning ?: 0} warnings)".toString())

            if (totalBySeverity.error)
                throw new LintCheckFailedException() // fail the whole build
        }
    }

    private static File relPath(File root, File f) {
        new File(root.toURI().relativize(f.toURI()).toString())
    }
}
