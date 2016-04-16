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
