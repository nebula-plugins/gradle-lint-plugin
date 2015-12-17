package com.netflix.nebula.lint.rule

class UnnecessaryDependencyTupleExpressionRuleSpec extends AbstractRuleSpec {
    def 'dependency tuples violate rule'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11'
            }
        """, new UnnecessaryDependencyTupleExpressionRule())

        then:
        results.violates(UnnecessaryDependencyTupleExpressionRule)
    }

    def 'violations are corrected'() {
        when:
        def results = correct("""
            dependencies {
               compile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """, new UnnecessaryDependencyTupleExpressionRule())

        then:
        results == """
            dependencies {
               compile 'junit:junit:4.11'
            }
        """
    }

    def 'dependency does not violate rule if it contains a secondary method call'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile dep('junit:junit')
            }
        """, new UnnecessaryDependencyTupleExpressionRule())

        then:
        results.doesNotViolate(UnnecessaryDependencyTupleExpressionRule)
    }

    def 'dependency does not violate rule if configuration is present'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11', conf: 'conf'
            }
        """, new UnnecessaryDependencyTupleExpressionRule())

        then:
        results.doesNotViolate(UnnecessaryDependencyTupleExpressionRule)
    }

    def 'exclude tuples do not violate rule'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile('junit:junit:4.11') {
                 exclude group: 'a'
               }
            }
        """, new UnnecessaryDependencyTupleExpressionRule())

        then:
        results.doesNotViolate(UnnecessaryDependencyTupleExpressionRule)
    }
}
