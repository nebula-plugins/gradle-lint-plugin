package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitorRule

class ParenthesesInDependencyRule extends AbstractAstVisitorRule {
    String name = 'UnnecessaryParenthesesInDependency'
    int priority = 3
    Class astVisitorClass = ParenthesesInDependencyAstVisitor
}

class ParenthesesInDependencyAstVisitor extends AbstractGradleLintVisitor {
    boolean inDependenciesBlock = false

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def args = call.arguments.expressions as List
        if(!args.empty && !(args[-1] instanceof ClosureExpression)) {
            def callSource = getSourceCode().line(call.lineNumber-1)
            if(callSource =~ "^${call.methodAsString}\\s*\\(") {
                addViolation(call, "Parentheses are unnecessary for dependency $callSource")
//                correctIfPossible(call)
            }
        }
    }
}