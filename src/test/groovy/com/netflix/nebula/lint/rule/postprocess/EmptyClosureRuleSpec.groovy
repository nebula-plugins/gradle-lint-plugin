package com.netflix.nebula.lint.rule.postprocess

import com.netflix.nebula.lint.postprocess.EmptyClosureRule
import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class EmptyClosureRuleSpec extends AbstractRuleSpec {
    def 'if a deletion rule(s) causes a closure to be empty, delete the closure'() {
        when:
        project.buildFile << """
            nebula {
                moduleOwner = 'me'
            }

            nebula {
            }
        """

        then:
        correct(new EmptyClosureRule()).replaceAll(/\s/, '') == /nebula{moduleOwner='me'}/
    }

    def 'do not delete empty tasks'() {
        when:
        project.buildFile << """
            task taskA {}
        """

        then:
        correct(new EmptyClosureRule()) == """
            task taskA {}
        """
    }
}
