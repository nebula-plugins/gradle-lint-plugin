package com.netflix.nebula.lint.plugin

import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.RuleSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging

class GradleLintPlugin implements Plugin<Project> {
    def logger = Logging.getLogger('lint')

    @Override
    void apply(Project project) {
        project.extensions.create('lint', GradleLintExtension)

        project.task('lint') << {
            def registry = new LintRuleRegistry(getClass().classLoader)
            def ruleSet = configureRuleSet(project.lint.rules.collect { registry.findRule(it) }
                    .findAll { it != null })

            def violations = new StringSourceAnalyzer(project.buildFile.text).analyze(ruleSet).violations

            violations.eachWithIndex { Violation v, Integer i ->
                if(i == 0)
                    logger.warn('')
                def severity = v.rule.priority <= 3 ? 'warning' : 'error'
                logger.warn(severity.padRight(10) + v.rule.name.padRight(25) + v.message)
                logger.warn(relPath(project.rootDir, project.buildFile).path + ':' + v.lineNumber)
                logger.warn(v.sourceLine)
                logger.warn('')
            }
        }
    }

    private static relPath(File root, File f) {
        new File(root.toURI().relativize(f.toURI()).toString())
    }

    private static RuleSet configureRuleSet(List<Rule> rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        ruleSet
    }
}
