/*
 * Copyright 2015-2016 Netflix, Inc.
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

package com.netflix.nebula.lint.rule.test

import com.netflix.nebula.lint.analyzer.CorrectableStringSourceAnalyzer
import nebula.test.ProjectSpec
import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.results.Results
import org.codenarc.rule.Rule
import org.codenarc.ruleregistry.RuleRegistryInitializer
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.RuleSet

abstract class AbstractRuleSpec extends ProjectSpec {
    def setupSpec() {
        Results.mixin ResultsAssert
    }

    private static RuleSet configureRuleSet(Rule... rules) {
        new RuleRegistryInitializer().initializeRuleRegistry()

        def ruleSet = new CompositeRuleSet()
        rules.each { ruleSet.addRule(it) }
        ruleSet
    }

    Results runRulesAgainst(Rule... rules) {
        new StringSourceAnalyzer(project.buildFile.text).analyze(configureRuleSet(rules))
    }

    String correct(Rule... rules) {
        def analyzer = new CorrectableStringSourceAnalyzer(project.buildFile.text)
        analyzer.analyze(configureRuleSet(rules))
        analyzer.corrected
    }
}

class ResultsAssert {
    boolean violates(Class<? extends Rule> ruleClass) {
        this.violations.find { v ->
            ruleClass.newInstance().name == v.rule.name
        }
    }

    boolean violates() {
        !this.violations.isEmpty()
    }

    boolean doesNotViolate(Class<? extends Rule> ruleClass) {
        !violates(ruleClass)
    }

    boolean doesNotViolate() {
        !violates()
    }
}