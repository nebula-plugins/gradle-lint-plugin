package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector

class OverriddenDependencyVersionRule extends GradleLintRule implements GradleModelAware {
    String description = 'be declarative about first order dependency versions that are changed by conflict resolution'

    def selectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator())

    def resolvableAndResolvedConfigurations

    @Override
    protected void beforeApplyTo() {
        def dependencyService = DependencyService.forProject(project)
        resolvableAndResolvedConfigurations = dependencyService.resolvableAndResolvedConfigurations()
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if (!resolvableAndResolvedConfigurations.collect { it.name }.contains(conf)) {
            return // we won't slow down the build by resolving the configuration if it hasn't been already
        }
        
        if(!dep.version) {
            return // we assume that anything recommending this version is behaving correctly
        }

        // causes the component metadata rule to fire and capture all metadata by module id
        def resolved = project.configurations.getByName(conf).resolvedConfiguration.resolvedArtifacts.find {
            it.moduleVersion.id.group == dep.group && it.moduleVersion.id.name == dep.name
        }

        if(!resolved)
            return

        // status is discarded by Gradle after resolution, so we have no way of getting at it at this point
        ComponentMetadata metadata = new ComponentMetadataAdapter(id: resolved.moduleVersion.id, status: 'unknown', statusScheme: Collections.emptyList())

        def selector = selectorScheme.parseSelector(dep.version)
        if(!(selector instanceof LatestVersionSelector) && !dep.version.startsWith('$') && !selector.accept(metadata)) {
            addBuildLintViolation('this version is not being used because of a conflict resolution, force, or resolution strategy', call)
                    .replaceWith(call, "$conf '$resolved.moduleVersion.id'")
        }
    }

    private class ComponentMetadataAdapter implements ComponentMetadata {
        ModuleVersionIdentifier id
        String status
        List<String> statusScheme

        final boolean changing = false // does not matter

//        @Override
        AttributeContainer getAttributes() {
            return null
        }
    }
}
