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

package com.netflix.nebula.lint.rule.test

import com.netflix.nebula.lint.GradleLintFix
import com.netflix.nebula.lint.GradleLintPatchAction
import com.netflix.nebula.lint.plugin.NotNecessarilyGitRepository
import com.netflix.nebula.lint.rule.BuildFiles
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import nebula.test.ProjectSpec
import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.results.Results
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.RuleSet
import org.eclipse.jgit.api.ApplyCommand

abstract class AbstractRuleSpec extends ProjectSpec {
    def setupSpec() {
        Results.mixin ResultsAssert
    }

    def setup() {
        project.configurations.create('compile')
    }

    private RuleSet configureRuleSet(GradleLintRule... rules) {
        def ruleSet = new CompositeRuleSet()
        rules.each {
            ruleSet.addRule(it)
            if (it instanceof GradleModelAware) {
                it.project = project
            }
        }
        ruleSet
    }

    private GradleLintRule[] configureBuildFile(GradleLintRule... rules) {
        rules.each {
            it.buildFiles = new BuildFiles([project.buildFile])
        }
        rules
    }

    Results runRulesAgainst(GradleLintRule... rules) {
        new StringSourceAnalyzer(project.buildFile.text).analyze(configureRuleSet(configureBuildFile(rules)))
    }

    String correct(GradleLintRule... rules) {
        def analyzer = new StringSourceAnalyzer(project.buildFile.text)
        def rulesWithBuildFile = configureBuildFile(rules)
        def violations = analyzer
                .analyze(configureRuleSet(*rulesWithBuildFile.collect { it.buildFiles.original(null).file = project.buildFile; it }))
                .violations

        def patchFile = new File(projectDir, 'lint.patch')
        patchFile.text = new GradleLintPatchAction(project).patch(
                violations*.fixes.flatten() as List<GradleLintFix>)

        new ApplyCommand(new NotNecessarilyGitRepository(projectDir)).setPatch(patchFile.newInputStream()).call()

        return project.buildFile.text
    }

    @Override
    boolean deleteProjectDir() {
        return false
    }
}

class ResultsAssert {
    boolean violates(Class<? extends GradleLintRule> ruleClass) {
        this.violations.find { v ->
            ruleClass.newInstance().name == v.rule.name
        }
    }

    boolean violates() {
        !this.violations.isEmpty()
    }

    boolean doesNotViolate(Class<? extends GradleLintRule> ruleClass) {
        !violates(ruleClass)
    }

    boolean doesNotViolate() {
        !violates()
    }
}