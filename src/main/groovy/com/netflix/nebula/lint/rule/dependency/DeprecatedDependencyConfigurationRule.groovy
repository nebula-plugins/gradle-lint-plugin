package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.interop.GradleKt
import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression

@CompileStatic
class DeprecatedDependencyConfigurationRule extends GradleLintRule implements GradleModelAware {
    String description = 'Replace deprecated configurations in dependencies'

    private final Map CONFIGURATION_REPLACEMENTS = [
            "compile": "implementation",
            "testCompile": "testImplementation",
            "runtime": "runtimeOnly"
    ]

    private final String MINIMUM_GRADLE_VERSION = "4.7"

    private final  String PROJECT_METHOD_NAME = 'project'

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        handleDependencyVisit(call, conf, dep)
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        handleDependencyVisit(call, conf, dep)
    }

    @Override
    void visitSubprojectGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        handleDependencyVisit(call, conf, dep)
    }

    @Override
    void visitAllprojectsGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        handleDependencyVisit(call, conf, dep)
    }

    @Override
    void visitDependencies(MethodCallExpression call) {
        if(!GradleKt.versionLessThan(project.gradle, MINIMUM_GRADLE_VERSION)) {
            handleProjectDependencies(call)
        }
    }

    /**
     * Responsible for replacing configurations in project dependencies only
     * @param call
     */
    @CompileDynamic
    private void handleProjectDependencies(MethodCallExpression call) {
        List statements = call.arguments.expressions*.code*.statements.flatten().findAll  { it.expression instanceof MethodCallExpression }
        statements.each { statement ->
            String configuration = statement.expression.method.value
            if(CONFIGURATION_REPLACEMENTS.containsKey(configuration)) {
                MethodCallExpression statementMethodCallExpression = statement.expression
                List<MethodCallExpression> projectDependencies = statementMethodCallExpression.arguments.expressions.flatten().findAll {
                    it instanceof MethodCallExpression && ((MethodCallExpression) it).methodAsString == PROJECT_METHOD_NAME
                }
                if(!projectDependencies) {
                    return
                }
                String project = extractProject(projectDependencies.first().arguments.expressions.first())
                if(!project) {
                    //Could not extract project as expression is not supported
                    return
                }
                String configurationReplacement = CONFIGURATION_REPLACEMENTS.get(configuration)
                addBuildLintViolation("Configuration $configuration has been deprecated and should be replaced with $configurationReplacement", statementMethodCallExpression)
            }
        }
    }

    @CompileDynamic
    private String extractProject(Expression expression) {
        if(expression instanceof ConstantExpression) {
            return expression.value
        } else {
            // other types not supportedd
            return null
        }
    }

    @CompileDynamic
    private void handleDependencyVisit(MethodCallExpression call, String conf, GradleDependency dep) {
        if(CONFIGURATION_REPLACEMENTS.containsKey(conf) && !GradleKt.versionLessThan(project.gradle, MINIMUM_GRADLE_VERSION)) {
            if (call.arguments.expressions.size() == 1) {
                replaceSingleLineDependencyConfiguration(call, conf, dep)
            } else {
                replaceMultiLineDependencyConfiguration(call, conf)
            }
        }
    }

    private void replaceSingleLineDependencyConfiguration(MethodCallExpression call, String conf, GradleDependency dep) {
        String configurationReplacement = CONFIGURATION_REPLACEMENTS.get(conf)
        addBuildLintViolation("Configuration $conf has been deprecated and should be replaced with $configurationReplacement", call)
    }

    private void replaceMultiLineDependencyConfiguration(MethodCallExpression call, String conf) {
        String configurationReplacement = CONFIGURATION_REPLACEMENTS.get(conf)
        addBuildLintViolation("Configuration $conf has been deprecated and should be replaced with $configurationReplacement", call)
    }
}
