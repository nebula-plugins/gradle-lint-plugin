package com.netflix.nebula.lint.rule.plugin

import com.netflix.nebula.lint.plugin.LintRuleRegistry
import org.codenarc.rule.AbstractAstVisitorRule
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class LintRuleRegistrySpec extends Specification {
    @Rule
    TemporaryFolder temp

    def 'load a rule with a single defined implementation class'() {
        setup:
        def singleRule = ruleFile('single-rule')
        singleRule << "implementation-class=${MockRule1.name}"

        when:
        def rules = new LintRuleRegistry(ruleClassLoader(), null).findRule('single-rule')

        then:
        rules.size() == 1
        rules[0] instanceof MockRule1
    }

    def 'load a rule that includes other rules'() {
        setup:
        def rule1 = ruleFile('rule1')
        rule1 << "implementation-class=${MockRule1.name}"

        def rule2 = ruleFile('rule2')
        rule2 << "implementation-class=${MockRule2.name}"

        def composite = ruleFile('composite')
        composite << 'includes=rule1,rule2'

        when:
        def rules = new LintRuleRegistry(ruleClassLoader(), null).findRule('composite')

        then:
        rules.size() == 2
        rules[0] instanceof MockRule1
        rules[1] instanceof MockRule2
    }

    private ClassLoader ruleClassLoader() {
        new URLClassLoader([temp.root.toURI().toURL()] as URL[], getClass().getClassLoader())
    }

    private File ruleFile(String ruleId) {
        new File(temp.root, 'META-INF/lint-rules').mkdirs()
        temp.newFile("META-INF/lint-rules/${ruleId}.properties")
    }

    static class MockRule1 extends AbstractAstVisitorRule {
        String name = 'rule1'
        int priority = 3
    }

    static class MockRule2 extends AbstractAstVisitorRule {
        String name = 'rule2'
        int priority = 3
    }
}
