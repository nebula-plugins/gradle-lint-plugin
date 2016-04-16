package com.netflix.nebula.lint.postprocess

import com.netflix.nebula.lint.rule.GradleLintRule
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

class EmptyClosureRule extends GradleLintRule {
    def potentialDeletes = [] as List<MethodCallExpression>

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        def expressions = call.arguments.expressions

        expressions.each {
            // prevents empty tasks from being deleted that take this form:
            // task taskA {}
            potentialDeletes.remove(it)
        }

        if (expressions.size == 1 && expressions.last() instanceof ClosureExpression) {
            def closure = expressions.last() as ClosureExpression
            if(closure.code.empty) {
                potentialDeletes.add(call)
            }
        }
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        potentialDeletes.unique().each {
            addLintViolation('this is an empty configuration closure that can be removed', it)
                .delete(it)
        }
    }
}
