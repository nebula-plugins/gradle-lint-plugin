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

class BypassedForcesRule extends GradleLintRule implements GradleModelAware {
    String description = 'remove unused forces from dependency resolution bypassing them'
    DependencyService dependencyService
    Collection<ForcedDependency> forcedDependencies = new ArrayList<ForcedDependency>()

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
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if (!call.arguments.metaClass.getMetaMethod('getExpressions')) {
            return // short-circuit if there are no expressions
        }

        if (call.arguments.expressions
                .findAll { it instanceof ClosureExpression }
                .any { closureContainsForce(it as ClosureExpression) }) {
            forcedDependencies.add(new ForcedDependency(dep, call, conf))
        }

        if (call.arguments.expressions
                .findAll { it instanceof ClosureExpression }
                .any { closureContainsVersionConstraintWithStrictVersion(it as ClosureExpression) }) {
            forcedDependencies.add(new ForcedDependency(dep, call, conf))
        }

    }

    private static Boolean closureContainsForce(ClosureExpression expr) {
        return expr.code.statements.any {
            (it.expression instanceof BinaryExpression) &&
                    ((BinaryExpression) it.expression).leftExpression?.variable == 'force' &&
                    ((BinaryExpression) it.expression).rightExpression?.value == true
        }
    }

    private static Boolean closureContainsVersionConstraintWithStrictVersion(ClosureExpression expr) {
        return expr.code.statements.any { st ->
            (st.expression instanceof MethodCallExpression) &&
                    ((MethodCallExpression) st.expression).arguments?.any { arg ->
                        arg instanceof ClosureExpression &&
                                arg?.code instanceof BlockStatement &&
                                arg?.code?.statements?.any { stmt ->
                                    stmt?.expression instanceof MethodCallExpression &&
                                            stmt?.expression?.method instanceof ConstantExpression &&
                                            ((ConstantExpression) stmt?.expression?.method)?.value == 'strictly' &&
                                            stmt?.expression?.arguments?.expressions?.any { expre ->
                                                expre instanceof ConstantExpression &&
                                                        !expre?.value?.equals(null)
                                            }
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

        configuration.resolvedConfiguration.firstLevelModuleDependencies.each { resolvedDep ->
            forcedDeps.each { forcedDependency ->
                if (forcedDependency.dep.group == resolvedDep.moduleGroup && forcedDependency.dep.name == resolvedDep.moduleName) {
                    if (resolvedDep.moduleVersion != forcedDependency.dep.version) {
                        forcedDependency.message = "The force specified for dependency '${resolvedDep.moduleGroup}:${resolvedDep.moduleName}' has been bypassed"
                        forcedDependency.resolvedConfigurations.add(configuration)
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
        String message

        ForcedDependency(GradleDependency dep, Expression forceExpression, String declaredConfigurationName) {
            this.dep = dep
            this.forceExpression = forceExpression
            this.declaredConfigurationName = declaredConfigurationName
        }

        void setMessage(String message) {
            this.message = message
        }
    }
}
