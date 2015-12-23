package com.netflix.nebula.lint.rule

class DependencyTupleExpressionRuleSpec extends AbstractRuleSpec {
    def 'dependency tuples violate rule'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11'
            }
        """, new DependencyTupleExpressionRule())

        then:
        results.violates(DependencyTupleExpressionRule)
    }

    def 'violations are corrected'() {
        when:
        def results = correct("""
            dependencies {
               compile group: 'junit',
                    name: 'junit',
                    version: '4.11'
               compile group: 'netflix', name: 'platform'
            }
        """, new DependencyTupleExpressionRule())

        then:
        results == """
            dependencies {
               compile 'junit:junit:4.11'
               compile 'netflix:platform'
            }
        """
    }

    def 'dependency does not violate rule if it contains a secondary method call'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile dep('junit:junit')
            }
        """, new DependencyTupleExpressionRule())

        then:
        results.doesNotViolate(DependencyTupleExpressionRule)
    }

    def 'dependency does not violate rule if configuration is present'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11', conf: 'conf'
            }
        """, new DependencyTupleExpressionRule())

        then:
        results.doesNotViolate(DependencyTupleExpressionRule)
    }

    def 'exclude tuples do not violate rule'() {
        when:
        def results = runRulesAgainst("""
            dependencies {
               compile('junit:junit:4.11') {
                 exclude group: 'a'
               }
            }
        """, new DependencyTupleExpressionRule())

        then:
        results.doesNotViolate(DependencyTupleExpressionRule)
    }
}
