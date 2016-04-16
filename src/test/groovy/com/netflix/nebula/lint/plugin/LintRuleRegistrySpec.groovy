package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.GradleLintRule
import org.gradle.api.Project
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class LintRuleRegistrySpec extends Specification {
    @Rule
    TemporaryFolder temp

    def setup() {
        LintRuleRegistry.classLoader = new URLClassLoader([temp.root.toURI().toURL()] as URL[], getClass().getClassLoader())
    }

    def 'load a rule with a single defined implementation class'() {
        setup:
        def project = Mock(Project)

        def singleRule = ruleFile('single-rule')
        singleRule << "implementation-class=${MockRule1.name}"

        when:
        def rules = new LintRuleRegistry().buildRules('single-rule', project)

        then:
        rules.size() == 1
        rules[0] instanceof MockRule1
        rules[0].ruleId == 'single-rule'
    }

    def 'load a rule that includes other rules'() {
        setup:
        def project = Mock(Project)

        def rule1 = ruleFile('rule1')
        rule1 << "implementation-class=${MockRule1.name}"

        def rule2 = ruleFile('rule2')
        rule2 << "implementation-class=${MockRule2.name}"

        def composite = ruleFile('composite')
        composite << 'includes=rule1,rule2'

        when:
        def rules = new LintRuleRegistry().buildRules('composite', project)

        then:
        rules.size() == 2
        rules[0] instanceof MockRule1
        rules[1] instanceof MockRule2
    }

    def 'list rule ids associated with a single rule id'() {
        setup:
        def rule1 = ruleFile('rule1')
        rule1 << "implementation-class=${MockRule1.name}"

        def rule2 = ruleFile('rule2')
        rule2 << "implementation-class=${MockRule2.name}"

        def composite = ruleFile('composite')
        composite << 'includes=rule1,rule2'

        when:
        def rules = new LintRuleRegistry().findRules('composite')

        then:
        rules.size() == 2
        rules[0] == 'rule1'
        rules[1] == 'rule2'
    }

    private File ruleFile(String ruleId) {
        new File(temp.root, 'META-INF/lint-rules').mkdirs()
        temp.newFile("META-INF/lint-rules/${ruleId}.properties")
    }

    static class MockRule1 extends GradleLintRule { }

    static class MockRule2 extends GradleLintRule { }
}
