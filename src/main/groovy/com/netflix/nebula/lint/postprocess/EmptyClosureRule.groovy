package com.netflix.nebula.lint.postprocess

import com.netflix.nebula.lint.rule.GradleLintRule
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

class EmptyClosureRule extends GradleLintRule {
    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        def expressions = call.arguments.expressions

        if (!expressions.isEmpty() && expressions.last() instanceof ClosureExpression) {
            def closure = expressions.last() as ClosureExpression
            if(closure.code.empty) {
                addViolationToDelete(call, 'this is an empty configuration closure that can be removed')
            }
        }
    }
}
