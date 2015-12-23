package com.netflix.nebula.lint.plugin

import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.RuleSet
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
        def registry = new LintRuleRegistry(getClass().classLoader)
        def ruleSet = configureRuleSet(project.extensions
                .getByType(GradleLintExtension)
                .rules
                .collect { registry.findRule(it) }
                .findAll { it != null })

        def textOutput = textOutputFactory.create('lint')

        def violations = new StringSourceAnalyzer(project.buildFile.text).analyze(ruleSet).violations

        violations.eachWithIndex { Violation v, Integer i ->
            if(i == 0)
                textOutput.println()

            def severity = v.rule.priority <= 3 ? 'warning' : 'error'

            textOutput.withStyle(StyledTextOutput.Style.Failure).text(severity.padRight(10))
            textOutput.text(v.rule.name.padRight(25))
            textOutput.withStyle(StyledTextOutput.Style.Description).text(v.message)
            textOutput.println()

            textOutput.withStyle(StyledTextOutput.Style.UserInput).text(relPath(project.rootDir, project.buildFile).path + ':' + v.lineNumber)
            textOutput.println()
            textOutput.text(v.sourceLine)
            textOutput.println('\n')
        }

        if(!violations.isEmpty()) {
            def totalBySeverity = violations.countBy { it.rule.priority <= 3 ? 'warning' : 'error' }
            textOutput.withStyle(StyledTextOutput.Style.Failure).text("\u2716 ${violations.size()} problems (${totalBySeverity.error ?: 0} errors, ${totalBySeverity.warning ?: 0} warnings)".toString())
            textOutput.println()
        }
    }

    protected static File relPath(File root, File f) {
        new File(root.toURI().relativize(f.toURI()).toString())
    }

    protected static RuleSet configureRuleSet(List<Rule> rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        ruleSet
    }
}
