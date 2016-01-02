package com.netflix.nebula.lint.rule.test

import com.netflix.nebula.lint.analyzer.CorrectableStringSourceAnalyzer
import nebula.test.ProjectSpec
import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.results.Results
import org.codenarc.rule.Rule
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.RuleSet

abstract class AbstractRuleSpec extends ProjectSpec {
    def setupSpec() {
        Results.mixin ResultsAssert
    }

    private static RuleSet configureRuleSet(Rule... rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        ruleSet
    }

    Results runRulesAgainst(Rule... rules) {
        new StringSourceAnalyzer(project.buildFile.text).analyze(configureRuleSet(rules))
    }

    String correct(Rule... rules) {
        def analyzer = new CorrectableStringSourceAnalyzer(project.buildFile.text)
        analyzer.analyze(configureRuleSet(rules))
        analyzer.corrected
    }
}

class ResultsAssert {
    boolean violates(Class<? extends Rule> ruleClass) {
        this.violations.find { v -> ruleClass.isAssignableFrom(v.rule.class) }
    }

    boolean doesNotViolate(Class<? extends Rule> ruleClass) {
        !violates(ruleClass)
    }
}