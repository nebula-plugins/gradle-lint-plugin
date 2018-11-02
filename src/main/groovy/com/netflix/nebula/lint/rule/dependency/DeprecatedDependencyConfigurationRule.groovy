package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.interop.GradleKt
import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression

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
                String project = projectDependencies.first().arguments.expressions.first().value
                String configurationReplacement = CONFIGURATION_REPLACEMENTS.get(configuration)
                GradleViolation violation = addBuildLintViolation("Configuration $configuration has been deprecated and should be replaced with $configurationReplacement", statementMethodCallExpression)
                DependencyViolationUtil.replaceProjectDependencyConfiguration(violation, statementMethodCallExpression, configurationReplacement, project)
            }
        }
    }

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
        GradleViolation violation = addBuildLintViolation("Configuration $conf has been deprecated and should be replaced with $configurationReplacement", call)
        DependencyViolationUtil.replaceDependencyConfiguration(violation, call, configurationReplacement, dep)
    }

    private void replaceMultiLineDependencyConfiguration(MethodCallExpression call, String conf) {
        String configurationReplacement = CONFIGURATION_REPLACEMENTS.get(conf)
        GradleViolation violation = addBuildLintViolation("Configuration $conf has been deprecated and should be replaced with $configurationReplacement", call)
        DependencyViolationUtil.replaceDependencyConfiguration(violation, call, configurationReplacement)
    }
}
