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

package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.GradleViolation
import org.codenarc.analyzer.StringSourceAnalyzer
import org.codenarc.rule.Rule
import org.gradle.api.Project

class GradleLintService {
    Collection<GradleViolation> lint(Project project) {
        def violations = []
        def registry = new LintRuleRegistry()

        ([project] + project.subprojects).each { p ->
            if (p.buildFile.exists()) {
                def extension
                try {
                    extension = p.extensions.getByType(GradleLintExtension)
                } catch (UnknownDomainObjectException) {
                    // if the subproject has not applied lint, use the extension configuration from the root project
                    extension = p.rootProject.extensions.getByType(GradleLintExtension)
                }
                def ruleSet = RuleSetFactory.configureRuleSet(extension.rules.collect { registry.buildRules(it, p) }
                        .flatten() as List<Rule>)

                violations.addAll(new StringSourceAnalyzer(p.buildFile.text).analyze(ruleSet).violations)
            }
        }

        return violations
    }
}
