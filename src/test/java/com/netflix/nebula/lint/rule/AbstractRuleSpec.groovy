package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.analyzer.CorrectableStringSourceAnalyzer
import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.results.Results
import org.codenarc.rule.Rule
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.PropertiesFileRuleSetConfigurer
import org.codenarc.ruleset.RuleSet
import spock.lang.Specification

abstract class AbstractRuleSpec extends Specification {
    def setupSpec() {
        Results.mixin ResultsAssert
    }

    private RuleSet configureRuleSet(Rule... rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        new PropertiesFileRuleSetConfigurer().configure(ruleSet)
        ruleSet
    }

    Results runRulesAgainst(String source, Rule... rules) {
        new StringSourceAnalyzer(source).analyze(configureRuleSet(rules))
    }

    String correct(String source, Rule... rules) {
        def analyzer = new CorrectableStringSourceAnalyzer(source)
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