package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

class DependencyParenthesesRule extends GradleLintRule {
    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def args = call.arguments.expressions as List
        if(!args.empty && !(args[-1] instanceof ClosureExpression)) {
            def callSource = getSourceCode().line(call.lineNumber-1)
            def matcher = callSource =~ /^${call.methodAsString}\s*\((?<dep>[^\)]+)/
            if(matcher.find()) {
                addLintViolation('parentheses are unnecessary for dependencies', call)
                    .replaceWith(call, "${call.methodAsString} ${matcher.group('dep')}")
            }
        }
    }
}