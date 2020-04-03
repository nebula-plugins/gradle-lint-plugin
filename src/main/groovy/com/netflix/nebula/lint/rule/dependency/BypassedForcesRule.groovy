package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector

class BypassedForcesRule extends GradleLintRule implements GradleModelAware {
    String description = 'remove unused forces from dependency resolution bypassing them'
    DependencyService dependencyService
    Collection<ForcedDependency> forcedDependencies = new ArrayList<ForcedDependency>()
    DefaultVersionComparator VERSIONED_COMPARATOR = new DefaultVersionComparator()
    DefaultVersionSelectorScheme VERSION_SCHEME = new DefaultVersionSelectorScheme(VERSIONED_COMPARATOR)

    @Override
    protected void beforeApplyTo() {
        dependencyService = DependencyService.forProject(project)
    }

    @Override
    void visitGradleResolutionStrategyForce(MethodCallExpression call, String conf, Map<GradleDependency, Expression> forces) {
        forces.each { dep, force ->
            forcedDependencies.add(new ForcedDependency(dep, force, conf))
        }
    }

    @Override
    void visitAnyGradleDependencyWithAForceClosure(MethodCallExpression call, String conf, GradleDependency dep) {
        forcedDependencies.add(new ForcedDependency(dep, call, conf))
    }

    @Override
    void visitAnyGradleDependencyWithVersionConstraint(MethodCallExpression call, String conf, GradleDependency dep, Map<String, List<String>> versionConstraints) {
        if (versionConstraints.containsKey('strictly')) {
            def strictlyValue = versionConstraints.get('strictly')
            def forcedDependency = new ForcedDependency(dep, call, conf)
            forcedDependency.setStrictVersion(strictlyValue.first())
            forcedDependencies.add(forcedDependency)
        }
    }

    @CompileStatic
    @Override
    protected void visitClassComplete(ClassNode node) {
        def groupedForcedDependencies = forcedDependencies
                .groupBy { it.declaredConfigurationName }
        def resolvableAndResolvedConfigurations = dependencyService.resolvableAndResolvedConfigurations()
        Collection<ForcedDependency> dependenciesWithUnusedForces = new ArrayList<ForcedDependency>()

        groupedForcedDependencies.each { declaredConfigurationName, forcedDeps ->
            if (declaredConfigurationName == 'all') {
                resolvableAndResolvedConfigurations.each { configuration ->
                    dependenciesWithUnusedForces.addAll(collectDependenciesWithUnusedForces(configuration, forcedDeps))
                }
            } else {
                Configuration groupedResolvableConfiguration = dependencyService.findResolvableConfiguration(declaredConfigurationName)
                if (resolvableAndResolvedConfigurations.contains(groupedResolvableConfiguration)) {
                    dependenciesWithUnusedForces.addAll(collectDependenciesWithUnusedForces(groupedResolvableConfiguration, forcedDeps))
                }
            }
        }
        
        dependenciesWithUnusedForces.groupBy { it.dep }.each { _dep, forcedDependenciesByDep ->
            forcedDependenciesByDep.groupBy { it.forceExpression }.each { _forceExpression, forcedDependenciesByDepAndForceExpression ->
                if (forcedDependenciesByDepAndForceExpression.size() > 0) {
                    ForcedDependency exemplarForcedDep = forcedDependenciesByDepAndForceExpression.first()
                    addBuildLintViolation(exemplarForcedDep.message, exemplarForcedDep.forceExpression)
                }
            }
        }

    }

    @CompileStatic
    Collection<ForcedDependency> collectDependenciesWithUnusedForces(Configuration configuration, Collection<ForcedDependency> forcedDeps) {
        Collection<ForcedDependency> dependenciesWithUnusedForces = new ArrayList<ForcedDependency>()

        // inspect direct and transitive dependencies
        configuration.resolvedConfiguration.resolvedArtifacts
                .collect { it.moduleVersion.id }
                .unique { it.module.toString() }
                .each { resolvedDep ->
                    forcedDeps.each { forcedDependency ->
                        if (forcedDependency.dep.group == resolvedDep.module.group && forcedDependency.dep.name == resolvedDep.name) {
                            def expectedVersion = ''
                            def providedMessage = ''
                            if (forcedDependency.dep.version != null) {
                                expectedVersion = forcedDependency.dep.version
                                providedMessage = "The dependency force has been bypassed. Remove or update this value"
                            }
                            if (!forcedDependency.strictVersion.isEmpty()) {
                                expectedVersion = forcedDependency.strictVersion
                                providedMessage = "The dependency strict version constraint has been bypassed. Remove or update this value"
                            }

                            VersionSelector versionSelector = VERSION_SCHEME.parseSelector(expectedVersion)
                            if (!versionSelector.accept(resolvedDep.version)
                                    && !expectedVersion.toString().contains(".+")
                                    && !expectedVersion.toString().contains("latest")) {

                                forcedDependency.resolvedConfigurations.add(configuration)
                                forcedDependency.message = providedMessage
                                dependenciesWithUnusedForces.add(forcedDependency)
                            }
                        }
                    }
                }

        return dependenciesWithUnusedForces
    }

    @CompileStatic
    class ForcedDependency {
        GradleDependency dep
        Expression forceExpression
        String declaredConfigurationName
        Collection<Configuration> resolvedConfigurations = new ArrayList<>()
        String message = ''
        String strictVersion = ''

        ForcedDependency(GradleDependency dep, Expression forceExpression, String declaredConfigurationName) {
            this.dep = dep
            this.forceExpression = forceExpression
            this.declaredConfigurationName = declaredConfigurationName
        }

        void setMessage(String message) {
            this.message = message
        }

        void setStrictVersion(String strictVersion) {
            this.strictVersion = strictVersion
        }
    }
}
