package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class DependencyParenthesesRuleSpec extends AbstractRuleSpec {
    def 'valid uses of parentheses pass'() {
        when:
        project.buildFile << """
            dependencies {
               compile 'junit:junit:4.11'
               compile ('a:a:1') { }
            }
        """
        def results = runRulesAgainst(new DependencyParenthesesRule())

        then:
        results.doesNotViolate(DependencyParenthesesRule)
    }

    def 'parenthesized dependency violates'() {
        when:
        project.buildFile << """
            dependencies {
               compile('junit:junit:4.11')
            }
        """
        def results = runRulesAgainst(new DependencyParenthesesRule())

        then:
        results.violates(DependencyParenthesesRule)
    }

    def 'parenthesized dependencies are corrected'() {
        when:
        project.buildFile << """
            dependencies {
               compile('junit:junit:4.11')
            }
        """
        def results = correct(new DependencyParenthesesRule())

        then:
        results == """
            dependencies {
               compile 'junit:junit:4.11'
            }
        """
    }
}
