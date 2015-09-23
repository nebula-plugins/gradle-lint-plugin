package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.GradleNamedClosureAstVisitor
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AbstractAstVisitorRule

class UnnecessaryParenthesesInDependencyRule extends AbstractAstVisitorRule {
    String name = 'UnnecessaryParentheses'
    int priority = 3
    Class astVisitorClass = UnnecessaryParenthesesInDependencyAstVisitor
}

class UnnecessaryParenthesesInDependencyAstVisitor extends GradleNamedClosureAstVisitor {
    static ClassCodeVisitorSupport dependenciesVisitor = new AbstractAstVisitor() {
        @Override
        void visitMethodCallExpression(MethodCallExpression call) {
            def args = (call.arguments as ArgumentListExpression).expressions
            if(!args.isEmpty() && !(args[-1] instanceof ClosureExpression)) {
                def callSource = getSourceCode().line(call.lineNumber-1)
                if(callSource =~ "^${call.methodAsString}\\s*\\(") {
                    addViolation(call, "Parentheses are unnecessary for dependency $callSource")
                }
            }
            super.visitMethodCallExpression(call)
        }
    }

    UnnecessaryParenthesesInDependencyAstVisitor() {
        super('dependencies', dependenciesVisitor)
    }
}