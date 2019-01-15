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

package com.netflix.nebula.lint.rule.dependency

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
