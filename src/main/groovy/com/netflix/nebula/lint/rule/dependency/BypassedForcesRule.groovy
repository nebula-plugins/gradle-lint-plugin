package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression

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
        if(!call.arguments.metaClass.getMetaMethod('getExpressions')) {
            return // short-circuit if there are no expressions
        }

        if(!call.arguments.expressions
            .findAll {it instanceof  ClosureExpression}
            .any { closureContainsForce(it as ClosureExpression) }){
            return // short-circuit if there are no forces
        }

        forcedDependencies.add(new ForcedDependency(dep, call, conf))
    }

    private static Boolean closureContainsForce(ClosureExpression expr) {
        return expr.code.statements.any { (it.expression instanceof BinaryExpression) &&
                ((BinaryExpression)it.expression).leftExpression?.variable == 'force' &&
                ((BinaryExpression)it.expression).rightExpression?.value == true }
    }

    @CompileStatic
    @Override
    protected void visitClassComplete(ClassNode node) {
        dependencyService.resolvableAndResolvedConfigurations().each { configuration ->
            configuration.resolvedConfiguration.firstLevelModuleDependencies.each { resolvedDep ->
                forcedDependencies
                        .findAll { configuration == dependencyService.findResolvableConfiguration(it.declaredConfigurationName) }
                        .each { forcedDependency ->
                            if (forcedDependency.dep.group == resolvedDep.moduleGroup && forcedDependency.dep.name == resolvedDep.moduleName) {
                                if (resolvedDep.moduleVersion != forcedDependency.dep.version) {
                                    addBuildLintViolation("The force specified for dependency '${resolvedDep.moduleGroup}:${resolvedDep.moduleName}' has been bypassed", forcedDependency.forceExpression)
                                }
                            }
                        }
            }

            configuration.resolvedConfiguration.resolvedArtifacts.each { resolvedArtifact ->
                forcedDependencies
                        .findAll { configuration == dependencyService.findResolvableConfiguration(it.declaredConfigurationName) }
                        .each { forcedDependency ->
                            if (forcedDependency.dep.group == resolvedArtifact.moduleVersion.id.group && forcedDependency.dep.name == resolvedArtifact.moduleVersion.id.name) {
                                if (resolvedArtifact.moduleVersion.id.version != forcedDependency.dep.version) {
                                    addBuildLintViolation("The force specified for artifact '${resolvedArtifact.moduleVersion.id.group}:${resolvedArtifact.moduleVersion.id.name}' has been bypassed", forcedDependency.forceExpression)
                                }
                            }
                        }
            }
        }
    }

    @CompileStatic
    class ForcedDependency {
        GradleDependency dep
        Expression forceExpression
        String declaredConfigurationName

        ForcedDependency(GradleDependency dep, Expression forceExpression, String declaredConfigurationName) {
            this.dep = dep
            this.forceExpression = forceExpression
            this.declaredConfigurationName = declaredConfigurationName
        }
    }
}
