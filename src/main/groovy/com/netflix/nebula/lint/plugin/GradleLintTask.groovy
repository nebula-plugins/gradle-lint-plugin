package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.GradleLintPatchGenerator
import com.netflix.nebula.lint.GradleLintViolationAction
import com.netflix.nebula.lint.GradleViolation
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

import static com.netflix.nebula.lint.GradleViolation.Level.Warning

class GradleLintTask extends DefaultTask {
    List<GradleLintViolationAction> listeners = []

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        null // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    }

    @TaskAction
    void lint() {
        def violations = new GradleLintService().lint(project)
        (listeners + new GradleLintPatchGenerator(project) + consoleOutputAction).each {
            it.lintFinished(violations)
        }
    }

    final def consoleOutputAction = new GradleLintViolationAction() {
        @Override
        void lintFinished(Collection<GradleViolation> violations) {
            def totalBySeverity = [(Warning): 0, (GradleViolation.Level.Error): 0] +
                    violations.countBy { it.level }

            def textOutput = textOutputFactory.create('lint')

            if (totalBySeverity[GradleViolation.Level.Error] > 0 || totalBySeverity[Warning] > 0) {
                textOutput.withStyle(StyledTextOutput.Style.UserInput).text('\nThis project contains lint violations. ')
                textOutput.println('A complete listing of the violations follows. ')

                if (totalBySeverity[GradleViolation.Level.Error]) {
                    textOutput.text('Because some were serious, the overall build status has been changed to ')
                            .withStyle(StyledTextOutput.Style.Failure).println("FAILED\n")
                } else {
                    textOutput.println('Because none were serious, the build\'s overall status was unaffected.\n')
                }
            }

            violations.groupBy { it.buildFile }.each { buildFile, projectViolations ->
                def buildFilePath = project.rootDir.toURI().relativize(buildFile.toURI()).toString()

                projectViolations.each { v ->
                    switch (v.level as GradleViolation.Level) {
                        case Warning:
                            textOutput.withStyle(StyledTextOutput.Style.Failure).text('warning'.padRight(10))
                            break
                        case GradleViolation.Level.Error:
                            textOutput.withStyle(StyledTextOutput.Style.Failure).text('error'.padRight(10))
                            break
                        case Info:
                            textOutput.withStyle(StyledTextOutput.Style.Description).text('info'.padRight(10))
                            break
                    }

                    textOutput.text(v.rule.ruleId.padRight(35))
                    textOutput.withStyle(StyledTextOutput.Style.Description).println(v.message)

                    if (v.lineNumber)
                        textOutput.withStyle(StyledTextOutput.Style.UserInput).println(buildFilePath + ':' + v.lineNumber)
                    if (v.sourceLine)
                        textOutput.println("$v.sourceLine")

                    textOutput.println() // extra space between violations
                }

                def errors = totalBySeverity[GradleViolation.Level.Error] ?: 0
                def warnings = totalBySeverity[Warning] ?: 0
                if (errors + warnings > 0) {
                    textOutput.withStyle(StyledTextOutput.Style.Failure)
                            .println("\u2716 ${buildFilePath}: ${errors + warnings} problem${errors + warnings == 1 ? '' : 's'} ($errors error${errors == 1 ? '' : 's'}, $warnings warning${warnings == 1 ? '' : 's'})\n".toString())
                }
            }

            if (totalBySeverity[GradleViolation.Level.Error] > 0 || totalBySeverity[Warning] > 0) {
                textOutput.text("To apply fixes automatically, run ").withStyle(StyledTextOutput.Style.UserInput).text("fixGradleLint")
                textOutput.println(", review, and commit the changes.\n")

                if (totalBySeverity.error)
                    throw new LintCheckFailedException() // fail the whole build
            }
        }
    }
}
