package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.EmptyExpression
import org.codehaus.groovy.ast.expr.LambdaExpression
import org.codehaus.groovy.ast.expr.MethodReferenceExpression

//tests are compiled against Groovy 3 main code against Groovy 2
//this rule declares new methods so we don't have to declare them in every test rule in tests
abstract class AbstractExampleGradleLintRule extends GradleLintRule {
    @Override
    void visitLambdaExpression(LambdaExpression lambdaExpression) {
    }

    @Override
    void visitMethodReferenceExpression(MethodReferenceExpression methodReferenceExpression) {
    }

    @Override
    void visitEmptyExpression(EmptyExpression expression) {
    }
}
