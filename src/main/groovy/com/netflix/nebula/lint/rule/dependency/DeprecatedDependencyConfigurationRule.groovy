package com.netflix.nebula.lint.rule.dependency

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

    private void handleDependencyVisit(MethodCallExpression call, String conf, GradleDependency dep) {
        if(CONFIGURATION_REPLACEMENTS.containsKey(conf)) {
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
