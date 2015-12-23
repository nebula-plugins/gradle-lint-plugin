package com.netflix.nebula.lint

import groovy.transform.TupleConstructor
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.codenarc.rule.AbstractAstVisitor
import org.codenarc.rule.AstVisitor
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.source.SourceCode

@TupleConstructor
class GradleNamedClosureAstVisitor extends AbstractAstVisitor {
    String name
    ClassCodeVisitorSupport closureVisitor

    /**
     * <code>visitMethodCallExpression</code> is not called for methods invoked inside a closure body
     * @param call
     */
    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(call.methodAsString == name) {
            if(call.arguments instanceof ArgumentListExpression) {
                def argList = call.arguments as ArgumentListExpression
                if(!argList.expressions.isEmpty() && argList.expressions[-1] instanceof ClosureExpression) {
                    def block = argList.expressions[-1].code
                    if(!block.isEmpty()) {
                        block.statements.each { Statement stmt ->
                            closureVisitor.visitClassCodeContainer(stmt)
                        }
                    }
                }
            }
        }
        super.visitMethodCallExpression(call)
    }

    @Override
    void setSourceCode(SourceCode sourceCode) {
        if(closureVisitor instanceof AstVisitor)
            (closureVisitor as AstVisitor).setSourceCode(sourceCode)
        super.setSourceCode(sourceCode)
    }

    @Override
    void setRule(Rule rule) {
        if(closureVisitor instanceof AstVisitor)
            (closureVisitor as AstVisitor).setRule(rule)
        super.setRule(rule)
    }

    @Override
    List<Violation> getViolations() {
        if(closureVisitor instanceof AstVisitor)
            return (closureVisitor as AstVisitor).violations + super.violations
        super.violations
    }
}
