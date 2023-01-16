package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.classgen.BytecodeExpression

/**
 * This class brings together visitor methods from GroovyClassVisitor, GroovyCodeVisitor,
 * CodeNarc's AbstractAstVisitor, and ClassCodeVisitorSupport.
 */
abstract class GroovyAstVisitor implements GroovyClassVisitor, GroovyCodeVisitor {
    void visitClassComplete(ClassNode node) {}
    void visitAnnotations(AnnotatedNode node) {}
    void visitPackage(PackageNode node) {}
    void visitImports(ModuleNode node) {}
    void visitClassCodeContainer(Statement node) {}
    void visitStatement(Statement statement) {}
    void visitListOfExpressions(List<? extends Expression> list) {}
    void visitObjectInitializerStatements(ClassNode node) {}
    void visitEmptyStatement(EmptyStatement statement) {}

    @Override void visitClass(ClassNode classNode) {}
    @Override void visitConstructor(ConstructorNode constructorNode) {}
    @Override void visitMethod(MethodNode methodNode) {}
    @Override void visitField(FieldNode fieldNode) {}
    @Override void visitProperty(PropertyNode propertyNode) {}
    @Override void visitBlockStatement(BlockStatement blockStatement) {}
    @Override void visitForLoop(ForStatement forStatement) {}
    @Override void visitWhileLoop(WhileStatement whileStatement) {}
    @Override void visitDoWhileLoop(DoWhileStatement doWhileStatement) {}
    @Override void visitIfElse(IfStatement ifStatement) {}
    @Override void visitExpressionStatement(ExpressionStatement expressionStatement) {}
    @Override void visitReturnStatement(ReturnStatement returnStatement) {}
    @Override void visitAssertStatement(AssertStatement assertStatement) {}
    @Override void visitTryCatchFinally(TryCatchStatement tryCatchStatement) {}
    @Override void visitSwitch(SwitchStatement switchStatement) {}
    @Override void visitCaseStatement(CaseStatement caseStatement) {}
    @Override void visitBreakStatement(BreakStatement breakStatement) {}
    @Override void visitContinueStatement(ContinueStatement continueStatement) {}
    @Override void visitThrowStatement(ThrowStatement throwStatement) {}
    @Override void visitSynchronizedStatement(SynchronizedStatement synchronizedStatement) {}
    @Override void visitCatchStatement(CatchStatement catchStatement) {}
    @Override void visitMethodCallExpression(MethodCallExpression methodCallExpression) {}
    @Override void visitStaticMethodCallExpression(StaticMethodCallExpression staticMethodCallExpression) {}
    @Override void visitConstructorCallExpression(ConstructorCallExpression constructorCallExpression) {}
    @Override void visitTernaryExpression(TernaryExpression ternaryExpression) {}
    @Override void visitShortTernaryExpression(ElvisOperatorExpression elvisOperatorExpression) {}
    @Override void visitBinaryExpression(BinaryExpression binaryExpression) {}
    @Override void visitPrefixExpression(PrefixExpression prefixExpression) {}
    @Override void visitPostfixExpression(PostfixExpression postfixExpression) {}
    @Override void visitBooleanExpression(BooleanExpression booleanExpression) {}
    @Override void visitClosureExpression(ClosureExpression closureExpression) {}
    @Override void visitTupleExpression(TupleExpression tupleExpression) {}
    @Override void visitMapExpression(MapExpression mapExpression) {}
    @Override void visitMapEntryExpression(MapEntryExpression mapEntryExpression) {}
    @Override void visitListExpression(ListExpression listExpression) {}
    @Override void visitRangeExpression(RangeExpression rangeExpression) {}
    @Override void visitPropertyExpression(PropertyExpression propertyExpression) {}
    @Override void visitAttributeExpression(AttributeExpression attributeExpression) {}
    @Override void visitFieldExpression(FieldExpression fieldExpression) {}
    @Override void visitMethodPointerExpression(MethodPointerExpression methodPointerExpression) {}
    @Override void visitConstantExpression(ConstantExpression constantExpression) {}
    @Override void visitClassExpression(ClassExpression classExpression) {}
    @Override void visitVariableExpression(VariableExpression variableExpression) {}
    @Override void visitDeclarationExpression(DeclarationExpression declarationExpression) {}
    @Override void visitGStringExpression(GStringExpression gStringExpression) {}
    @Override void visitArrayExpression(ArrayExpression arrayExpression) {}
    @Override void visitSpreadExpression(SpreadExpression spreadExpression) {}
    @Override void visitSpreadMapExpression(SpreadMapExpression spreadMapExpression) {}
    @Override void visitNotExpression(NotExpression notExpression) {}
    @Override void visitUnaryMinusExpression(UnaryMinusExpression unaryMinusExpression) {}
    @Override void visitUnaryPlusExpression(UnaryPlusExpression unaryPlusExpression) {}
    @Override void visitBitwiseNegationExpression(BitwiseNegationExpression bitwiseNegationExpression) {}
    @Override void visitCastExpression(CastExpression castExpression) {}
    @Override void visitArgumentlistExpression(ArgumentListExpression argumentListExpression) {}
    @Override void visitClosureListExpression(ClosureListExpression closureListExpression) {}
    @Override void visitBytecodeExpression(BytecodeExpression bytecodeExpression) {}
    @Override void visitLambdaExpression(LambdaExpression lambdaExpression) {}
    @Override void visitMethodReferenceExpression(MethodReferenceExpression methodReferenceExpression) {}
}
