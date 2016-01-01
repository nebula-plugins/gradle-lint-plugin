package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitorRule

class DependencyTupleExpressionRule extends AbstractAstVisitorRule {
    String name = 'dependency-tuple'
    int priority = 3
    Class astVisitorClass = DependencyTupleExpressionAstVisitor
}

class DependencyTupleExpressionAstVisitor extends AbstractGradleLintVisitor {
    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if(dep.conf == null && dep.syntax == GradleDependency.Syntax.MapNotation) {
            addViolationWithReplacement(call, "use the shortcut form of the dependency", correction(call))
        }
    }

    static String correction(MethodCallExpression m) {
        def args = (m.arguments.expressions.find { it instanceof MapExpression } as MapExpression)
                .mapEntryExpressions
        def group = '', artifact = '', version = ''
        args.each {
            def val = it.valueExpression.text
            switch(it.keyExpression.text) {
            case 'group':
                group = val; break
            case 'name':
                artifact = val; break
            case 'version':
                version = val; break
            }
        }

        // FIXME all properties except name are optional according to https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
        "${m.methodAsString} '$group:$artifact${version ? ":$version" : ''}'"
    }
}
