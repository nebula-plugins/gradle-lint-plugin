/*
 * Copyright 2015-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule.postprocess

import com.netflix.nebula.lint.postprocess.EmptyClosureRule
import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class EmptyClosureRuleSpec extends AbstractRuleSpec {
    def 'if a deletion rule(s) causes a closure to be empty, delete the closure'() {
        when:
        project.buildFile << """
            nebula { moduleOwner = 'me' }
            nebula { }
            """.substring(1).stripIndent()

        then:
        correct(new EmptyClosureRule()).replaceAll(/\s/, '') == /nebula{moduleOwner='me'}/
    }

    def 'if a deletion rule(s) may be limited to a subset of blocks'() {
        when:
        project.buildFile << """
            nebula { moduleOwner = 'me' }
            test { }
            """.substring(1).stripIndent()

        def rule = new EmptyClosureRule()
        rule.enableDeletableBlocks = true
        rule.deletableBlocks.add('nebula')

        then:
        correct(rule).replaceAll(/\s/, '') == /nebula{moduleOwner='me'}test{}/
    }

    def 'if a deletion rule(s) may be limited to a subset of blocks and deletes if in list'() {
        when:
        project.buildFile << """
            nebula { moduleOwner = 'me' }
            nebula { }
            """.substring(1).stripIndent()

        def rule = new EmptyClosureRule()
        rule.enableDeletableBlocks = true
        rule.deletableBlocks.add('nebula')

        then:
        correct(rule).replaceAll(/\s/, '') == /nebula{moduleOwner='me'}/
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
