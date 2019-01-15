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

package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.GradleLintRule
import org.gradle.api.Project
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class LintRuleRegistrySpec extends Specification {
    @Rule
    TemporaryFolder temp

    def setup() {
        LintRuleRegistry.classLoader = new URLClassLoader([temp.root.toURI().toURL()] as URL[], getClass().getClassLoader())
    }

    def 'load a rule with a single defined implementation class'() {
        setup:
        def project = Mock(Project)

        def singleRule = ruleFile('single-rule')
        singleRule << "implementation-class=${MockRule1.name}"

        when:
        def rules = new LintRuleRegistry().buildRules('single-rule', project, false)

        then:
        rules.size() == 1
        rules[0] instanceof MockRule1
        rules[0].ruleId == 'single-rule'
    }

    def 'load a rule that includes other rules'() {
        setup:
        def project = Mock(Project)

        def rule1 = ruleFile('rule1')
        rule1 << "implementation-class=${MockRule1.name}"

        def rule2 = ruleFile('rule2')
        rule2 << "implementation-class=${MockRule2.name}"

        def composite = ruleFile('composite')
        composite << 'includes=rule1,rule2'

        when:
        def rules = new LintRuleRegistry().buildRules('composite', project, false)

        then:
        rules.size() == 2
        rules[0] instanceof MockRule1
        rules[1] instanceof MockRule2
    }

    def 'list rule ids associated with a single rule id'() {
        setup:
        def rule1 = ruleFile('rule1')
        rule1 << "implementation-class=${MockRule1.name}"

        def rule2 = ruleFile('rule2')
        rule2 << "implementation-class=${MockRule2.name}"

        def composite = ruleFile('composite')
        composite << 'includes=rule1,rule2'

        when:
        def rules = new LintRuleRegistry().findRules('composite')

        then:
        rules.size() == 2
        rules[0] == 'rule1'
        rules[1] == 'rule2'
    }

    private File ruleFile(String ruleId) {
        new File(temp.root, 'META-INF/lint-rules').mkdirs()
        temp.newFile("META-INF/lint-rules/${ruleId}.properties")
    }

    static class MockRule1 extends GradleLintRule {
        String description = 'mock1'
    }

    static class MockRule2 extends GradleLintRule {
        String description = 'mock2'
    }
}
