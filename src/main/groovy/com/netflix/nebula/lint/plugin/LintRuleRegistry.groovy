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
                return included + [(Rule) classLoader.loadClass(implClassName).newInstance()]
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new InvalidRuleException(String.format(
                        "Could not find or load implementation class '%s' for rule '%s' specified in %s.", implClassName, ruleId, ruleDescriptor), e)
            }
        } else {
            return included
        }
    }
}
