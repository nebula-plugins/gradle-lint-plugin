package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.rule.dependency.DependencyParenthesesRule
import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class DependencyParenthesesRuleSpec extends AbstractRuleSpec {
    def rule = new DependencyParenthesesRule()

    def 'valid uses of parentheses pass'() {
        when:
        project.buildFile << """
            dependencies {
               compile 'junit:junit:4.11'
               compile ('a:a:1') { }
            }
        """
        def results = runRulesAgainst(rule)

        then:
        results.doesNotViolate()
    }

    def 'parenthesized dependency violates'() {
        when:
        project.buildFile << """
            dependencies {
               compile('junit:junit:4.11')
            }
        """
        def results = runRulesAgainst(rule)

        then:
        results.violates()
    }

    def 'parenthesized dependencies are corrected'() {
        when:
        project.buildFile << """
            dependencies {
               compile('junit:junit:4.11')
            }
        """
        def results = correct(rule)

        then:
        results == """
            dependencies {
               compile 'junit:junit:4.11'
            }
        """
    }
}
