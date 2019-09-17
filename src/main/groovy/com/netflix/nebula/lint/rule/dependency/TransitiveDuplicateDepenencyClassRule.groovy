package com.netflix.nebula.lint.rule.dependency

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier

@CompileStatic
class TransitiveDuplicateDepenencyClassRule extends AbstractDuplicateDependencyClassRule {
    @Override
    protected List<ModuleVersionIdentifier> moduleIds(Configuration conf) {
        return transitiveModuleIds(conf) - firstOrderModuleIds(conf)
    }
}
