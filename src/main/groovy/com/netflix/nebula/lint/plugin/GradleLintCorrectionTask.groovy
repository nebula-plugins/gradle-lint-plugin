package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.GradleLintPatchGenerator
import com.netflix.nebula.lint.GradleLintViolationAction
import com.netflix.nebula.lint.GradleViolation
import org.eclipse.jgit.api.ApplyCommand
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

import javax.inject.Inject

class GradleLintCorrectionTask extends DefaultTask {
    List<GradleLintViolationAction> listeners = []

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        null // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    }

    @TaskAction
    void lintCorrections() {
        // look at org.gradle.logging.internal.DefaultColorMap
        def violations = new GradleLintService().lint(project)
        (listeners + new GradleLintPatchGenerator(project) + consoleOutputAction).each {
            it.lintFinished(violations)
            it.lintFixed(violations.findAll { it.isFixable() })
        }

        def patchFile = new File(project.buildDir, GradleLintPatchGenerator.PATCH_NAME)
        if(patchFile.exists()) {
            new ApplyCommand(new NotNecessarilyGitRepository(project.projectDir)).setPatch(patchFile.newInputStream()).call()
        }
    }

    final def consoleOutputAction = new GradleLintViolationAction() {
        @Override
        void lintFixed(Collection<GradleViolation> violations) {
            def textOutput = textOutputFactory.create('lint')

            if(violations.empty) {
                textOutput.style(StyledTextOutput.Style.Identifier).println("Passed lint check with 0 violations; no corrections necessary")
            } else {
                textOutput.withStyle(StyledTextOutput.Style.UserInput).text('\nThis project contains lint violations. ')
                textOutput.println('A complete listing of my attempt to fix them follows. Please review and commit the changes.\n')

                violations.groupBy { it.buildFile }.each { buildFile, projectViolations ->
                    def buildFilePath = project.rootDir.toURI().relativize(buildFile.toURI()).toString()

                    projectViolations.each { v ->
                        textOutput.withStyle(StyledTextOutput.Style.Identifier).text('fixed'.padRight(10))
                        textOutput.text(v.rule.ruleId.padRight(35))
                        textOutput.withStyle(StyledTextOutput.Style.Description).println(v.message)

                        if(v.lineNumber)
                            textOutput.withStyle(StyledTextOutput.Style.UserInput).println(buildFilePath + ':' + v.lineNumber)
                        if (v.sourceLine)
                            textOutput.println("$v.sourceLine")

                        textOutput.println() // extra space between violations
                    }
                }

                textOutput.style(StyledTextOutput.Style.Identifier).println("Corrected ${violations.size()} lint problems\n")
            }
        }
    }
}
