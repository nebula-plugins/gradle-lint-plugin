package com.netflix.nebula.lint.plugin

import com.google.common.base.Preconditions
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codenarc.rule.Rule
import org.gradle.api.Project

class LintRuleRegistry {
    private final Project project
    static ClassLoader classLoader = null

    LintRuleRegistry(Project project) {
        this.project = project
    }

    private static LintRuleDescriptor findRuleDescriptor(String ruleId) {
        assert classLoader != null
        URL resource = classLoader.getResource(String.format("META-INF/lint-rules/%s.properties", ruleId))
        return resource ? new LintRuleDescriptor(resource) : null
    }

    static List<Class> findVisitorClassNames(String ruleId) {
        assert classLoader != null
        def ruleDescriptor = findRuleDescriptor(ruleId)
        if (ruleDescriptor == null)
            return []

        if(ruleDescriptor.implementationClassName)
            return [classLoader.loadClass(ruleDescriptor.implementationClassName).newInstance().astVisitor.class]
        else
            return (ruleDescriptor.includes?.collect { findVisitorClassNames(it as String) }?.flatten() ?: []) as List<Class>
    }

    List<Rule> findRule(String ruleId) {
        assert classLoader != null
        def ruleDescriptor = findRuleDescriptor(ruleId)
        if (ruleDescriptor == null)
            return []

        def implClassName = ruleDescriptor.implementationClassName
        def includes = ruleDescriptor.includes

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
