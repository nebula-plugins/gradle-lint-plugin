package com.netflix.nebula.lint.rule.test

import com.netflix.nebula.lint.GradleLintFix
import com.netflix.nebula.lint.GradleLintPatchGenerator
import com.netflix.nebula.lint.plugin.NotNecessarilyGitRepository
import com.netflix.nebula.lint.rule.GradleLintRule
import nebula.test.ProjectSpec
import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.results.Results
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.RuleSet
import org.eclipse.jgit.api.ApplyCommand

abstract class AbstractRuleSpec extends ProjectSpec {
    def setupSpec() {
        Results.mixin ResultsAssert
    }

    private static RuleSet configureRuleSet(GradleLintRule... rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        ruleSet
    }

    Results runRulesAgainst(GradleLintRule... rules) {
        new StringSourceAnalyzer(project.buildFile.text).analyze(configureRuleSet(rules))
    }

    String correct(GradleLintRule... rules) {
        def analyzer = new StringSourceAnalyzer(project.buildFile.text)
        def violations = analyzer.analyze(configureRuleSet(*rules.collect { it.buildFile = project.buildFile; it })).violations

        def patchFile = new File(projectDir, 'lint.patch')
        patchFile.text = new GradleLintPatchGenerator(project).patch(
                violations*.fixes.flatten() as List<GradleLintFix>)

        new ApplyCommand(new NotNecessarilyGitRepository(projectDir)).setPatch(patchFile.newInputStream()).call()

        return project.buildFile.text
    }
}

class ResultsAssert {
    boolean violates(Class<? extends GradleLintRule> ruleClass) {
        this.violations.find { v ->
            ruleClass.newInstance().name == v.rule.name
        }
    }

    boolean violates() {
        !this.violations.isEmpty()
    }

    boolean doesNotViolate(Class<? extends GradleLintRule> ruleClass) {
        !violates(ruleClass)
    }

    boolean doesNotViolate() {
        !violates()
    }
}