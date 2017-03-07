package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GroovyClassVisitor
import org.codehaus.groovy.ast.GroovyCodeVisitor
import org.codehaus.groovy.ast.ImportNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PackageNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ArrayExpression
import org.codehaus.groovy.ast.expr.AttributeExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BitwiseNegationExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.expr.CastExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ClosureListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.FieldExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.MethodPointerExpression
import org.codehaus.groovy.ast.expr.NotExpression
import org.codehaus.groovy.ast.expr.PostfixExpression
import org.codehaus.groovy.ast.expr.PrefixExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.RangeExpression
import org.codehaus.groovy.ast.expr.SpreadExpression
import org.codehaus.groovy.ast.expr.SpreadMapExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.UnaryMinusExpression
import org.codehaus.groovy.ast.expr.UnaryPlusExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.AssertStatement
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.BreakStatement
import org.codehaus.groovy.ast.stmt.CaseStatement
import org.codehaus.groovy.ast.stmt.CatchStatement
import org.codehaus.groovy.ast.stmt.ContinueStatement
import org.codehaus.groovy.ast.stmt.DoWhileStatement
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ForStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.SwitchStatement
import org.codehaus.groovy.ast.stmt.SynchronizedStatement
import org.codehaus.groovy.ast.stmt.ThrowStatement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.ast.stmt.WhileStatement
import org.codehaus.groovy.classgen.BytecodeExpression

/**
 * This class brings together visitor methods from GroovyClassVisitor, GroovyCodeVisitor,
 * CodeNarc's AbstractAstVisitor, and ClassCodeVisitorSupport.
 */
abstract class GroovyAstVisitor implements GroovyClassVisitor, GroovyCodeVisitor {
    protected void visitClassComplete(ClassNode node) {}
    protected void visitAnnotations(AnnotatedNode node) {}
    protected void visitPackage(PackageNode node) {}
    protected void visitImports(ModuleNode node) {}
    protected void visitClassCodeContainer(Statement node) {}
    protected void visitStatement(Statement statement) {}
    protected void visitListOfExpressions(List<? extends Expression> list) {}
    protected void visitObjectInitializerStatements(ClassNode node) {}
    protected void visitEmptyStatement(EmptyStatement statement) {}

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
}
