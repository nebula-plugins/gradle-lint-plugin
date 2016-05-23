package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme

class OverriddenDependencyVersionRule extends GradleLintRule implements GradleModelAware {
    String description = 'be declarative about first order dependency versions that are changed by conflict resolution'

    def selectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator())

    Map<ModuleIdentifier, ComponentMetadata> metadataByModuleId = [:]

    @Override
    protected void beforeApplyTo() {
        project.dependencies.components.all { details ->
            metadataByModuleId[details.id.module] = new ComponentMetadataAdapter(id: details.id, status: details.status, statusScheme: details.statusScheme)
        }
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        // causes the component metadata rule to fire and capture all metadata by module id
        def resolved = project.configurations.getByName(conf).resolvedConfiguration.resolvedArtifacts.find {
            it.moduleVersion.id.group == dep.group && it.moduleVersion.id.name == dep.name
        }

        if(!resolved)
            return

        ComponentMetadata metadata = metadataByModuleId[dep.toModuleVersion().module] ?:
                new ComponentMetadataAdapter(id: resolved.moduleVersion.id, status: 'unknown', statusScheme: ['unknown'])

        if(!selectorScheme.parseSelector(dep.version).accept(metadata)) {
            addBuildLintViolation('this version is not being used because of a conflict resolution, force, or resolution strategy', call)
                    .replaceWith(call, "$conf '$resolved.moduleVersion.id'")
        }
    }

    private class ComponentMetadataAdapter implements ComponentMetadata {
        ModuleVersionIdentifier id
        String status
        List<String> statusScheme

        final boolean changing = false // does not matter
    }
}
