package com.netflix.nebula.lint.rule

import org.codenarc.analyzer.SourceAnalyzer
import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.results.Results
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.PropertiesFileRuleSetConfigurer
import spock.lang.Specification

abstract class AbstractRuleSpec extends Specification {
    def setupSpec() {
        Results.mixin ResultsAssert
    }

    Results runRulesAgainst(String source, Rule... rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        new PropertiesFileRuleSetConfigurer().configure(ruleSet)

        SourceAnalyzer sourceAnalyzer = new StringSourceAnalyzer(source);
        sourceAnalyzer.analyze(ruleSet)
    }
}

class ResultsAssert {
    boolean violates(Class<? extends Rule> ruleClass) {
        this.violations.find { Violation v -> ruleClass.isAssignableFrom(v.rule.class) }
    }

    boolean doesNotViolate(Class<? extends Rule> ruleClass) {
        !violates(ruleClass)
    }
}