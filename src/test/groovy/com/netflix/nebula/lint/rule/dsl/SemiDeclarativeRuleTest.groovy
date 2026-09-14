package com.netflix.nebula.lint.rule.dsl

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class SemiDeclarativeRuleTest extends AbstractRuleSpec {
    def 'visit class'() {
        when:
        project.buildFile << """
            b = 2
            a = 1
            class A {
                int a
                A() {
                    a = 1
                }
            }
        """

        final var results = runRulesAgainst(new SemiDeclarativeRule())

        then:
        results.violations.size() == 1
    }
}
