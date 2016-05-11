package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme

class OverriddenDependencyVersionRule extends GradleLintRule implements GradleModelAware {
    String description = 'be declarative about first order dependency versions that are changed by conflict resolution'

    def selectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator())

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        ComponentMetadata metadata = null

        // must be added before we re-resolve the configuration
        project.dependencies.components.withModule(dep.toModuleVersion().module) { details ->
            metadata = new ComponentMetadataAdapter(id: details.id, status: details.status, statusScheme: details.statusScheme)
        }

        def resolved = project.configurations.getByName(conf).resolvedConfiguration.resolvedArtifacts.find {
            it.moduleVersion.id.group == dep.group && it.moduleVersion.id.name == dep.name
        }

        if(!resolved || !metadata)
            return

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
