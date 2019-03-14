package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import groovy.transform.TupleConstructor
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression


class MultiProjectCircularDependencyRule extends GradleLintRule implements GradleModelAware {
    String description = 'Detect circular dependencies in multi projects'

    private final String PROJECT_METHOD_NAME = 'project'
    private final String PROJECT_GRADLE_REFERENCE = ':'
    private final String EMPTY_STRING = ''
    static final List<ProjectDependency> allProjectDependencies = []

    @Override
    void visitDependencies(MethodCallExpression call) {
        List<MethodCallExpression> projectDependecies = findProjectDependecies(call)
        if(!projectDependecies) {
            return
        }

        projectDependecies.each { MethodCallExpression projectDependency ->
            String dependsOn = findProjectName(projectDependency)
            if(!dependsOn) {
                return
            }
            ProjectDependency dependency = new ProjectDependency(project.name, dependsOn)
            allProjectDependencies << dependency
            if (isCircularDependency(allProjectDependencies, dependency)) {
                addBuildLintViolation("Multi-project circular dependencies are not allowed. Circular dependency found between projects '$dependency.name' and '$dependency.dependsOn'", call)
            }
        }
    }

    private String findProjectName(Expression projectDependency) {
        String projectName = projectDependency?.arguments?.expressions?.find { it instanceof ConstantExpression }?.value
        if(!projectName) {
            return null
        }
        return projectName.replace(PROJECT_GRADLE_REFERENCE, EMPTY_STRING)
    }


    private List<MethodCallExpression> findProjectDependecies(MethodCallExpression call) {
        List expressions = call.arguments.expressions*.code*.statements.flatten().expression
        List<MethodCallExpression> dependencyExpressions = expressions.findAll { it instanceof MethodCallExpression }.arguments.expressions.flatten().findAll {
            it instanceof MethodCallExpression && ((MethodCallExpression) it).methodAsString == PROJECT_METHOD_NAME
        }
        return dependencyExpressions
    }

    private boolean isCircularDependency(List<ProjectDependency> projectDependencies, ProjectDependency current) {
        projectDependencies.any { projectDependency ->
            projectDependency.name == current.dependsOn && projectDependency.dependsOn == current.name
        }
    }

    @TupleConstructor
    private static class ProjectDependency {
        String name
        String dependsOn
    }
}
