package com.netflix.nebula.lint.plugin

import org.codenarc.rule.Rule

class LintRuleRegistry {
    private final ClassLoader classLoader

    LintRuleRegistry(ClassLoader classLoader) {
        this.classLoader = classLoader
    }

    private LintRuleDescriptor findRuleDescriptor(String ruleId) {
        URL resource = classLoader.getResource(String.format("META-INF/lint-rules/%s.properties", ruleId))
        return resource ? new LintRuleDescriptor(resource) : null
    }

    Rule findRule(String ruleId) {
        def ruleDescriptor = findRuleDescriptor(ruleId)
        if (ruleDescriptor == null)
            return null

        def implClassName = ruleDescriptor.getImplementationClassName()
        if (!implClassName) {
            throw new InvalidRuleException(String.format("No implementation class specified for rule '%s' in %s.", ruleId, ruleDescriptor))
        }

        try {
            return (Rule) classLoader.loadClass(implClassName).newInstance()
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new InvalidRuleException(String.format(
                    "Could not find or load implementation class '%s' for rule '%s' specified in %s.", implClassName, ruleId, ruleDescriptor), e)
        }
    }
}
