package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.control.SourceUnit
import org.codenarc.rule.AstVisitor
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.source.SourceCode

/**
 * AST visitor that delegates to both the gradleAstVisitor and the user-defined rule in that order.
 * Keeping them separate helps prevent user-defined visitors from inadvertently breaking the assumptions of
 * gradleAstVisitor. gradleAstVisitor also directly informs the user-defined rule when we have reached
 * certain recognizable Gradle constructs.
 */
class CompositeGroovyAstVisitor extends ClassCodeVisitorSupport implements AstVisitor {
    List<GroovyAstVisitor> visitors
    Stack<MethodCallExpression> callStack

    @Override
    protected SourceUnit getSourceUnit() {
        throw new RuntimeException("should never be called")
    }

    @Override
    void setRule(Rule rule) {

    }

    @Override
    void setSourceCode(SourceCode sourceCode) {

    }

    @Override
    List<Violation> getViolations() {
        return [] // FIXME
    }

    @Override
    void visitClass(ClassNode node) {
        visitors.each { it.visitClass(node) }
        super.visitClass(node)
        visitors.each { it.visitClassComplete(node) }
    }

    @Override
    protected void visitObjectInitializerStatements(ClassNode node) {
        visitors.each { it.visitObjectInitializerStatements(node) }
        super.visitObjectInitializerStatements(node)
    }

    @Override
    void visitPackage(PackageNode node) {
        visitors.each { it.visitPackage(node) }
        super.visitPackage(node)
    }

    @Override
    void visitImports(ModuleNode node) {
        visitors.each { it.visitImports(node) }
        super.visitImports(node)
    }

    @Override
    void visitAnnotations(AnnotatedNode node) {
        visitors.each { it.visitAnnotations(node) }
        super.visitAnnotations(node)
    }

    @Override
    protected void visitClassCodeContainer(Statement code) {
        visitors.each { it.visitClassCodeContainer(code) }
        super.visitClassCodeContainer(code)
    }

    @Override
    void visitDeclarationExpression(DeclarationExpression expression) {
        visitors.each { it.visitDeclarationExpression(expression) }
        super.visitDeclarationExpression(expression)
    }

    @Override
    protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        // within a method or constructor declaration, this code is not actually being invoked
        if (node.declaringClass.name == "None") {
            super.visitConstructorOrMethod(node, isConstructor)
        }
    }

    @Override
    void visitConstructor(ConstructorNode node) {
        // within a method or constructor declaration, this code is not actually being invoked
        if (node.declaringClass.name == "None") {
            visitors.each { it.visitConstructor(node) }
            super.visitConstructor(node)
        }
    }

    @Override
    void visitMethod(MethodNode node) {
        // within a method or constructor declaration, this code is not actually being invoked
        if (node.declaringClass.name == "None") {
            visitors.each { it.visitMethod(node) }
            super.visitMethod(node)
        }
    }

    @Override
    void visitField(FieldNode node) {
        visitors.each { it.visitField(node) }
        super.visitField(node)
    }

    @Override
    void visitProperty(PropertyNode node) {
        visitors.each { it.visitProperty(node) }
        super.visitProperty(node)
    }

    @Override
    protected void visitStatement(Statement statement) {
        visitors.each { it.visitStatement(statement) }
        super.visitStatement(statement)
    }

    @Override
    void visitAssertStatement(AssertStatement statement) {
        visitors.each { it.visitAssertStatement(statement) }
        super.visitAssertStatement(statement)
    }

    @Override
    void visitBlockStatement(BlockStatement block) {
        visitors.each { it.visitBlockStatement(block) }
        super.visitBlockStatement(block)
    }

    @Override
    void visitBreakStatement(BreakStatement statement) {
        visitors.each { it.visitBreakStatement(statement) }
        super.visitBreakStatement(statement)
    }

    @Override
    void visitCaseStatement(CaseStatement statement) {
        visitors.each { it.visitCaseStatement(statement) }
        super.visitCaseStatement(statement)
    }

    @Override
    void visitCatchStatement(CatchStatement statement) {
        visitors.each { it.visitCatchStatement(statement) }
        super.visitCatchStatement(statement)
    }

    @Override
    void visitContinueStatement(ContinueStatement statement) {
        visitors.each { it.visitContinueStatement(statement) }
        super.visitContinueStatement(statement)
    }

    @Override
    void visitDoWhileLoop(DoWhileStatement loop) {
        visitors.each { it.visitDoWhileLoop(loop) }
        super.visitDoWhileLoop(loop)
    }

    @Override
    void visitExpressionStatement(ExpressionStatement statement) {
        visitors.each { it.visitExpressionStatement(statement) }
        super.visitExpressionStatement(statement)
    }

    @Override
    void visitForLoop(ForStatement forLoop) {
        visitors.each { it.visitForLoop(forLoop) }
        super.visitForLoop(forLoop)
    }

    @Override
    void visitIfElse(IfStatement ifElse) {
        visitors.each { it.visitIfElse(ifElse) }
        super.visitIfElse(ifElse)
    }

    @Override
    void visitReturnStatement(ReturnStatement statement) {
        visitors.each { it.visitReturnStatement(statement) }
        super.visitReturnStatement(statement)
    }

    @Override
    void visitSwitch(SwitchStatement statement) {
        visitors.each { it.visitSwitch(statement) }
        super.visitSwitch(statement)
    }

    @Override
    void visitSynchronizedStatement(SynchronizedStatement statement) {
        visitors.each { it.visitSynchronizedStatement(statement) }
        super.visitSynchronizedStatement(statement)
    }

    @Override
    void visitThrowStatement(ThrowStatement statement) {
        visitors.each { it.visitThrowStatement(statement) }
        super.visitThrowStatement(statement)
    }

    @Override
    void visitTryCatchFinally(TryCatchStatement statement) {
        visitors.each { it.visitTryCatchFinally(statement) }
        super.visitTryCatchFinally(statement)
    }

    @Override
    void visitWhileLoop(WhileStatement loop) {
        visitors.each { it.visitWhileLoop(loop) }
        super.visitWhileLoop(loop)
    }

    @Override
    void visitEmptyStatement(EmptyStatement statement) {
        visitors.each { it.visitEmptyStatement(statement) }
        super.visitEmptyStatement(statement)
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        visitors.each { it.visitMethodCallExpression(call) }
        callStack.push(call)
        super.visitMethodCallExpression(call)
        callStack.pop()
    }

    @Override
    void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
        visitors.each { it.visitStaticMethodCallExpression(call) }
        super.visitStaticMethodCallExpression(call)
    }

    @Override
    void visitConstructorCallExpression(ConstructorCallExpression call) {
        visitors.each { it.visitConstructorCallExpression(call) }
        super.visitConstructorCallExpression(call)
    }

    @Override
    void visitBinaryExpression(BinaryExpression expression) {
        visitors.each { it.visitBinaryExpression(expression) }
        super.visitBinaryExpression(expression)
    }

    @Override
    void visitTernaryExpression(TernaryExpression expression) {
        visitors.each { it.visitTernaryExpression(expression) }
        super.visitTernaryExpression(expression)
    }

    @Override
    void visitShortTernaryExpression(ElvisOperatorExpression expression) {
        visitors.each { it.visitShortTernaryExpression(expression) }
        super.visitShortTernaryExpression(expression)
    }

    @Override
    void visitPostfixExpression(PostfixExpression expression) {
        visitors.each { it.visitPostfixExpression(expression) }
        super.visitPostfixExpression(expression)
    }

    @Override
    void visitPrefixExpression(PrefixExpression expression) {
        visitors.each { it.visitPrefixExpression(expression) }
        super.visitPrefixExpression(expression)
    }

    @Override
    void visitBooleanExpression(BooleanExpression expression) {
        visitors.each { it.visitBooleanExpression(expression) }
        super.visitBooleanExpression(expression)
    }

    @Override
    void visitNotExpression(NotExpression expression) {
        visitors.each { it.visitNotExpression(expression) }
        super.visitNotExpression(expression)
    }

    @Override
    void visitClosureExpression(ClosureExpression expression) {
        visitors.each { it.visitClosureExpression(expression) }
        super.visitClosureExpression(expression)
    }

    @Override
    void visitTupleExpression(TupleExpression expression) {
        visitors.each { it.visitTupleExpression(expression) }
        super.visitTupleExpression(expression)
    }

    @Override
    void visitListExpression(ListExpression expression) {
        visitors.each { it.visitListExpression(expression) }
        super.visitListExpression(expression)
    }

    @Override
    void visitArrayExpression(ArrayExpression expression) {
        visitors.each { it.visitArrayExpression(expression) }
        super.visitArrayExpression(expression)
    }

    @Override
    void visitMapExpression(MapExpression expression) {
        visitors.each { it.visitMapExpression(expression) }
        super.visitMapExpression(expression)
    }

    @Override
    void visitMapEntryExpression(MapEntryExpression expression) {
        visitors.each { it.visitMapEntryExpression(expression) }
        super.visitMapEntryExpression(expression)
    }

    @Override
    void visitRangeExpression(RangeExpression expression) {
        visitors.each { it.visitRangeExpression(expression) }
        super.visitRangeExpression(expression)
    }

    @Override
    void visitSpreadExpression(SpreadExpression expression) {
        visitors.each { it.visitSpreadExpression(expression) }
        super.visitSpreadExpression(expression)
    }

    @Override
    void visitSpreadMapExpression(SpreadMapExpression expression) {
        visitors.each { it.visitSpreadMapExpression(expression) }
        super.visitSpreadMapExpression(expression)
    }

    @Override
    void visitMethodPointerExpression(MethodPointerExpression expression) {
        visitors.each { it.visitMethodPointerExpression(expression) }
        super.visitMethodPointerExpression(expression)
    }

    @Override
    void visitUnaryMinusExpression(UnaryMinusExpression expression) {
        visitors.each { it.visitUnaryMinusExpression(expression) }
        super.visitUnaryMinusExpression(expression)
    }

    @Override
    void visitUnaryPlusExpression(UnaryPlusExpression expression) {
        visitors.each { it.visitUnaryPlusExpression(expression) }
        super.visitUnaryPlusExpression(expression)
    }

    @Override
    void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
        visitors.each { it.visitBitwiseNegationExpression(expression) }
        super.visitBitwiseNegationExpression(expression)
    }

    @Override
    void visitCastExpression(CastExpression expression) {
        visitors.each { it.visitCastExpression(expression) }
        super.visitCastExpression(expression)
    }

    @Override
    void visitConstantExpression(ConstantExpression expression) {
        visitors.each { it.visitConstantExpression(expression) }
        super.visitConstantExpression(expression)
    }

    @Override
    void visitClassExpression(ClassExpression expression) {
        visitors.each { it.visitClassExpression(expression) }
        super.visitClassExpression(expression)
    }

    @Override
    void visitVariableExpression(VariableExpression expression) {
        visitors.each { it.visitVariableExpression(expression) }
        super.visitVariableExpression(expression)
    }

    @Override
    void visitPropertyExpression(PropertyExpression expression) {
        visitors.each { it.visitPropertyExpression(expression) }
        super.visitPropertyExpression(expression)
    }

    @Override
    void visitAttributeExpression(AttributeExpression expression) {
        visitors.each { it.visitAttributeExpression(expression) }
        super.visitAttributeExpression(expression)
    }

    @Override
    void visitFieldExpression(FieldExpression expression) {
        visitors.each { it.visitFieldExpression(expression) }
        super.visitFieldExpression(expression)
    }

    @Override
    void visitGStringExpression(GStringExpression expression) {
        visitors.each { it.visitGStringExpression(expression) }
        super.visitGStringExpression(expression)
    }

    @Override
    void visitListOfExpressions(List<? extends Expression> list) {
        visitors.each { it.visitListOfExpressions(list) }
        //Groovy has a issue when calling java default methods In Groovy 3 `super.visitListOfExpressions(list)`
        //is implemented as default methods. The code is extracted and directly placed here to avoid the issue
        //https://stackoverflow.com/questions/54822838/explicitly-calling-a-default-method-in-groovy
        if (list != null) {
            list.each {
                it.visit(this)
            }
        }
    }

    @Override
    void visitArgumentlistExpression(ArgumentListExpression ale) {
        visitors.each { it.visitArgumentlistExpression(ale) }
        super.visitArgumentlistExpression(ale)
    }

    @Override
    void visitClosureListExpression(ClosureListExpression cle) {
        visitors.each { it.visitClosureListExpression(cle) }
        super.visitClosureListExpression(cle)
    }

    @Override
    void visitBytecodeExpression(BytecodeExpression cle) {
        visitors.each { it.visitBytecodeExpression(cle) }
        super.visitBytecodeExpression(cle)
    }
}
