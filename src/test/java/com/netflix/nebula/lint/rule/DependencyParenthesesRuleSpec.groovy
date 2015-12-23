package com.netflix.nebula.lint.rule

class DependencyParenthesesRuleSpec extends AbstractRuleSpec {
    def 'valid uses of parentheses pass'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile 'junit:junit:4.11'
               compile ('a:a:1') { }
            }
        """, new DependencyParenthesesRule())

        then:
        results.doesNotViolate(ParenthesesInDependencyRule)
    }

    def 'parenthesized dependency violates'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile('junit:junit:4.11')
            }
        """, new DependencyParenthesesRule())

        then:
        results.violates(ParenthesesInDependencyRule)
    }
}
