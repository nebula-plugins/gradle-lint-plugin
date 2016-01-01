package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitorRule

class DependencyParenthesesRule extends AbstractAstVisitorRule {
    String name = 'dependency-parentheses'
    int priority = 3
    Class astVisitorClass = DependencyParenthesesAstVisitor
}

class DependencyParenthesesAstVisitor extends AbstractGradleLintVisitor {
    boolean inDependenciesBlock = false

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def args = call.arguments.expressions as List
        if(!args.empty && !(args[-1] instanceof ClosureExpression)) {
            def callSource = getSourceCode().line(call.lineNumber-1)
            def matcher = callSource =~ /^${call.methodAsString}\s*\((?<dep>[^\)]+)/
            if(matcher.find()) {
                addViolationWithReplacement(call, 'parentheses are unnecessary for dependencies',
                        "${call.methodAsString} ${matcher.group('dep')}")
            }
        }
    }
}