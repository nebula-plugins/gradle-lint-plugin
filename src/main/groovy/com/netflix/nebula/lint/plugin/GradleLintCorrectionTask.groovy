package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.analyzer.CorrectableStringSourceAnalyzer
import org.codenarc.rule.Rule
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
        def registry = new LintRuleRegistry(getClass().classLoader)
        def ruleSet = RuleSetFactory.configureRuleSet(project.extensions
                .getByType(GradleLintExtension)
                .rules
                .collect { registry.findRule(it) }
                .flatten() as List<Rule>)

        def textOutput = textOutputFactory.create('lint')

        def analyzer = new CorrectableStringSourceAnalyzer(project.buildFile.text)
        def results = analyzer.analyze(ruleSet)

        project.buildFile.newWriter().withWriter { w ->
            w << analyzer.corrected
        }

        textOutput.style(StyledTextOutput.Style.Identifier).text("Corrected ${results.violations.size()} lint problems")
        textOutput.println()
    }
}
