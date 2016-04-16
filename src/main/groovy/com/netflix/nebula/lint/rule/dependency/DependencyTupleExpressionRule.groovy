package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleAstUtil
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import org.codehaus.groovy.ast.expr.MethodCallExpression

class DependencyTupleExpressionRule extends GradleLintRule {

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if(dep.conf == null && dep.syntax == GradleDependency.Syntax.MapNotation) {
            // FIXME what if one of the values is a function call?
            def ex = GradleAstUtil.collectEntryExpressions(call)
            addLintViolation('use the shortcut form of the dependency', call)
                .replaceWith(call, "${call.methodAsString} '${ex.group ?: ''}:${ex.name}${ex.version ? ":$ex.version" : ''}'")
        }
    }
}
