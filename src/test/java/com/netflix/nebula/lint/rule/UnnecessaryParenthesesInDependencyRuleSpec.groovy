package com.netflix.nebula.lint.rule

class UnnecessaryParenthesesInDependencyRuleSpec extends AbstractRuleSpec {
    def 'valid uses of parentheses pass'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile 'junit:junit:4.11'
               compile ('a:a:1') { }
            }
        """, new UnnecessaryParenthesesInDependencyRule())

        then:
        results.doesNotViolate(UnnecessaryParenthesesInDependencyRule)
    }

    def 'parenthesized dependency violates'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile('junit:junit:4.11')
            }
        """, new UnnecessaryParenthesesInDependencyRule())

        then:
        results.violates(UnnecessaryParenthesesInDependencyRule)
    }

    def 'parenthesized exclude violates'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile('junit:junit:4.11') {
                 exclude(module: 'a')
               }
            }
        """, new UnnecessaryParenthesesInDependencyRule())

        then:
        results.violates(UnnecessaryParenthesesInDependencyRule)
    }
}
