package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
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
            forcedDependencies.add(new ForcedDependency(dep, force, conf, 'dependency force'))
        }
    }

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if (!call.arguments.metaClass.getMetaMethod('getExpressions')) {
            return // short-circuit if there are no expressions
        }

        def top = dslStack().isEmpty() ? "" : dslStack().first()
        if (top == 'buildscript') {
            return // do not pay attention to buildscript dependencies at this time
        }

        handleForceInAClosure(call, conf, dep)
        handleVersionConstraintWithStrictVersion(call, conf, dep)
    }

    private void handleForceInAClosure(MethodCallExpression call, String conf, GradleDependency dep) {
        if (call.arguments.expressions
                .findAll { it instanceof ClosureExpression }
                .any { closureContainsForce(it as ClosureExpression) }) {
            forcedDependencies.add(new ForcedDependency(dep, call, conf, 'dependency force'))
        }
    }

    private void handleVersionConstraintWithStrictVersion(MethodCallExpression call, String conf, GradleDependency dep) {
        List<Map<String, List<String>>> versionConstraintsForAllExpressions = call.arguments.expressions
                .findAll { it instanceof ClosureExpression }
                .collect { gatherVersionConstraints(it as ClosureExpression) }
        if (versionConstraintsForAllExpressions.size() > 0) {
            versionConstraintsForAllExpressions.each { versionConstraints ->
                if (versionConstraints.containsKey('strictly')) {
                    def strictlyValue = versionConstraints.get('strictly')
                    def forcedDependency = new ForcedDependency(dep, call, conf, 'strict version constraint')
                    forcedDependency.setStrictVersion(strictlyValue.first())
                    forcedDependencies.add(forcedDependency)
                }
            }
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
                            if (forcedDependency.dep.version != null) {
                                expectedVersion = forcedDependency.dep.version
                            }
                            if (!forcedDependency.strictVersion.isEmpty()) {
                                expectedVersion = forcedDependency.strictVersion
                            }

                            VersionSelector versionSelector = VERSION_SCHEME.parseSelector(expectedVersion)
                            if (!versionSelector.accept(resolvedDep.version)
                                    && !expectedVersion.toString().contains(".+")
                                    && !expectedVersion.toString().contains("latest")) {

                                forcedDependency.resolvedConfigurations.add(configuration)
                                forcedDependency.message = "The ${forcedDependency.forceType} has been bypassed. Remove or update this value"
                                dependenciesWithUnusedForces.add(forcedDependency)
                            }
                        }
                    }
                }

        return dependenciesWithUnusedForces
    }

    private static Boolean closureContainsForce(ClosureExpression expr) {
        return expr.code.statements.any {
            (it.expression instanceof BinaryExpression) &&
                    ((BinaryExpression) it.expression).leftExpression?.variable == 'force' &&
                    ((BinaryExpression) it.expression).rightExpression?.value == true
        }
    }

    private static Map<String, List<String>> gatherVersionConstraints(ClosureExpression expr) {
        def results = new HashMap<String, List<String>>()
        expr.code.statements.findAll { st ->
            (st.expression instanceof MethodCallExpression) &&
                    ((MethodCallExpression) st.expression)?.methodAsString == 'version' &&
                    ((MethodCallExpression) st.expression)?.arguments?.findAll { arg ->
                        arg instanceof ClosureExpression &&
                                arg?.code instanceof BlockStatement &&
                                arg?.code?.statements?.findAll { stmt ->
                                    stmt?.expression instanceof MethodCallExpression &&
                                            stmt?.expression?.method instanceof ConstantExpression &&
                                            stmt?.expression?.arguments?.expressions?.findAll { expre ->
                                                expre instanceof ConstantExpression &&
                                                        !expre?.value?.equals(null)
                                            }
                                                    ?.each {
                                                        String method = stmt?.expression?.methodAsString
                                                        List values = stmt?.expression?.arguments?.expressions?.collect { it.value as String }

                                                        results.put(method, values)
                                                    }
                                }
                    }
        }
        return results
    }

    @CompileStatic
    class ForcedDependency {
        GradleDependency dep
        Expression forceExpression
        String declaredConfigurationName
        Collection<Configuration> resolvedConfigurations = new ArrayList<>()
        String message = ''
        String strictVersion = ''
        String forceType

        ForcedDependency(GradleDependency dep, Expression forceExpression, String declaredConfigurationName, String forceType) {
            this.dep = dep
            this.forceExpression = forceExpression
            this.declaredConfigurationName = declaredConfigurationName
            this.forceType = forceType
        }

        void setMessage(String message) {
            this.message = message
        }

        void setStrictVersion(String strictVersion) {
            this.strictVersion = strictVersion
        }
    }
}
