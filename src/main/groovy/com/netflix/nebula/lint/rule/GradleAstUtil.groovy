package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

class GradleAstUtil {
    static Map<String, String> collectEntryExpressions(MethodCallExpression call) {
        call.arguments.expressions
                .findAll { it instanceof MapExpression }
                .collect { it.mapEntryExpressions }
                .flatten()
                .collectEntries { [it.keyExpression.text, it.valueExpression.text] } as Map<String, String>
    }
}
