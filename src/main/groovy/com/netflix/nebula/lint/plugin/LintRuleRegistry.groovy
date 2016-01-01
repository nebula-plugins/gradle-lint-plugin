package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.GradleModelAware
import org.codenarc.rule.Rule
import org.gradle.api.Project

class LintRuleRegistry {
    private final ClassLoader classLoader
    private final Project project

    LintRuleRegistry(ClassLoader classLoader, Project project) {
        this.classLoader = classLoader
        this.project = project
    }

    private LintRuleDescriptor findRuleDescriptor(String ruleId) {
        URL resource = classLoader.getResource(String.format("META-INF/lint-rules/%s.properties", ruleId))
        return resource ? new LintRuleDescriptor(resource) : null
    }

    List<Rule> findRule(String ruleId) {
        def ruleDescriptor = findRuleDescriptor(ruleId)
        if (ruleDescriptor == null)
            return []

        def implClassName = ruleDescriptor.getImplementationClassName()
        def includes = ruleDescriptor.getIncludes()

        if (!implClassName && includes.isEmpty()) {
            throw new InvalidRuleException(String.format("No implementation class or includes specified for rule '%s' in %s.", ruleId, ruleDescriptor))
        }

        def included = includes.collect { findRule(it as String) }.flatten() as List<Rule>

        if(implClassName) {
            try {
                Rule r = (Rule) classLoader.loadClass(implClassName).newInstance()
                if(r instanceof GradleModelAware) {
                    (r as GradleModelAware).project = project
                }
                return included + r
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new InvalidRuleException(String.format(
                        "Could not find or load implementation class '%s' for rule '%s' specified in %s.", implClassName, ruleId, ruleDescriptor), e)
            }
        } else {
            return included
        }
    }
}
