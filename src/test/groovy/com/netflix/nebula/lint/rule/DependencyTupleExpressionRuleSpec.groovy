package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class DependencyTupleExpressionRuleSpec extends AbstractRuleSpec {
    def 'dependency tuples violate rule'() {
        when:
        project.buildFile << """
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11'
            }
        """
        def results = runRulesAgainst(new DependencyTupleExpressionRule())

        then:
        results.violates(DependencyTupleExpressionRule)
    }

    def 'violations are corrected'() {
        when:
        project.buildFile << """
            dependencies {
               compile group: 'junit',
                    name: 'junit',
                    version: '4.11'
               compile group: 'netflix', name: 'platform'
            }
        """
        def results = correct(new DependencyTupleExpressionRule())

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
        project.buildFile << """
            dependencies {
               compile dep('junit:junit')
            }
        """
        def results = runRulesAgainst(new DependencyTupleExpressionRule())

        then:
        results.doesNotViolate(DependencyTupleExpressionRule)
    }

    def 'dependency does not violate rule if configuration is present'() {
        when:
        project.buildFile << """
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11', conf: 'conf'
            }
        """
        def results = runRulesAgainst(new DependencyTupleExpressionRule())

        then:
        results.doesNotViolate(DependencyTupleExpressionRule)
    }

    def 'exclude tuples do not violate rule'() {
        when:
        project.buildFile << """
            dependencies {
               compile('junit:junit:4.11') {
                 exclude group: 'a'
               }
            }
        """
        def results = runRulesAgainst(new DependencyTupleExpressionRule())

        then:
        results.doesNotViolate(DependencyTupleExpressionRule)
    }
}
