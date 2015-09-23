package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AbstractAstVisitorRule

class UnnecessaryParenthesesInDependencyRule extends AbstractAstVisitorRule {
    String name = 'UnnecessaryParentheses'
    int priority = 3
    Class astVisitorClass = UnnecessaryParenthesesInDependencyAstVisitor
}

class UnnecessaryParenthesesInDependencyAstVisitor extends AbstractAstVisitor {
    boolean inDependenciesBlock = false

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(inDependenciesBlock) {
            def args = call.arguments.expressions as List
            if(!args.empty && !(args[-1] instanceof ClosureExpression)) {
                def callSource = getSourceCode().line(call.lineNumber-1)
                if(callSource =~ "^${call.methodAsString}\\s*\\(") {
                    addViolation(call, "Parentheses are unnecessary for dependency $callSource")
                }
            }
        }

        if(call.methodAsString == 'dependencies')
            inDependenciesBlock = true

        super.visitMethodCallExpression(call)

        if(call.methodAsString == 'dependencies')
            inDependenciesBlock = false
    }
}