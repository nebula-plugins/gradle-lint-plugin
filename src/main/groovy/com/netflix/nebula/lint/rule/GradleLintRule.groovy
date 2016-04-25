/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.plugin.LintRuleRegistry
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codenarc.rule.*
import org.codenarc.source.SourceCode


abstract class GradleLintRule extends AbstractAstVisitor implements Rule, GradleAstVisitor {
    private static final GradleViolation.Level DEFAULT_LEVEL = GradleViolation.Level.Warning
    Project project // will be non-null if type is GradleModelAware, otherwise null
    File buildFile
    SourceCode sourceCode
    List<GradleViolation> gradleViolations = []

    // a little convoluted, but will be set by LintRuleRegistry automatically so that name is derived from
    // the properties file resource that makes this rule available for use
    String ruleId

    boolean globalIgnoreOn = false
    List<String> rulesToIgnore = []

    @Override
    final int getPriority() {
        return DEFAULT_LEVEL.priority
    }

    private Map<String, ASTNode> bookmarks = [:]

    @Override void visitApplyPlugin(MethodCallExpression call, String plugin) {}
    @Override void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {}
    @Override void visitConfigurationExclude(MethodCallExpression call, String conf, GradleDependency exclude) {}
    @Override void visitExtensionProperty(ExpressionStatement expression, String extension, String prop, String value) {}
    @Override void visitExtensionProperty(ExpressionStatement expression, String extension, String prop) {}

    protected boolean isIgnored() {
        globalIgnoreOn || rulesToIgnore.collect { LintRuleRegistry.findRules(it) }.flatten().contains(ruleId)
    }

    @Override
    final MethodCallExpression parentClosure() {
        rule.closureStack.isEmpty() ? null : rule.closureStack.peek()
    }

    @Override
    final List<MethodCallExpression> closureStack() {
        new ArrayList<MethodCallExpression>(rule.closureStack as List)
    }

    /**
     * Used to preserve the location of a block of code so that it can be affected in some way
     * later in the AST visit
     */
    @Override
    void bookmark(String label, ASTNode node) {
        bookmarks[label] = node
    }

    @Override
    ASTNode bookmark(String label) {
        bookmarks[label]
    }

    @SuppressWarnings("GrDeprecatedAPIUsage")
    @Override
    protected final void addViolation(ASTNode node) {
        throw new UnsupportedOperationException("use one of the addViolationWith* methods or addViolationNoCorrection if there is no auto-fix rule")
    }

    @Override
    protected final void addViolation(ASTNode node, String message) {
        throw new UnsupportedOperationException("use one of the addViolationWith* methods or addViolationNoCorrection if there is no auto-fix rule")
    }

    public GradleViolation addLintViolation(String message, ASTNode node, GradleViolation.Level level = DEFAULT_LEVEL) {
        def v = new GradleViolation(level, buildFile, rule, node?.lineNumber, formattedViolation(node), message)
        if(!isIgnored())
            gradleViolations.add(v)
        return v
    }

    public GradleViolation addLintViolation(String message, GradleViolation.Level level = DEFAULT_LEVEL) {
        addLintViolation(message, null, level)
    }

    /**
     * Allows a rule to perform one-off processing before a rule is applied.
     */
    protected void beforeApplyTo() {}

    @Override
    final List<Violation> applyTo(SourceCode sourceCode) {
        beforeApplyTo()
        this.sourceCode = sourceCode
        rule.applyTo(sourceCode)
    }

    /**
     * @param node
     * @return a single or multi-line code snippet stripped of indentation, code that exists on the starting line
     * prior to the starting column, and code that exists on the last line after the ending column
     */
    private final String formattedViolation(ASTNode node) {
        if(!node) return null

        // make a copy of violating lines so they can be formatted for display in a report
        def violatingLines = new ArrayList<String>(sourceCode.lines.subList(node.lineNumber - 1, node.lastLineNumber))

        violatingLines[0] = violatingLines[0][(node.columnNumber - 1)..-1]
        if (node.lineNumber != node.lastLineNumber) {
            violatingLines[-1] = violatingLines[-1][0..(node.lastColumnNumber - 2)]
        }

        // taken from the internal implementation of stripIndent()
        def findMinimumLeadingSpaces = { Integer count, String line ->
            int index
            for(index = 0; index < line.length() && index < count && Character.isWhitespace(line.charAt(index)); ++index) {
                ;
            }
            index
        }

        def indentFirst = violatingLines.size() > 1 ? violatingLines.drop(1).inject(Integer.MAX_VALUE, findMinimumLeadingSpaces) : 0
        violatingLines[0] = violatingLines[0].padLeft(violatingLines[0].length()+indentFirst)
        violatingLines.join('\n').stripIndent()
    }

    /**
     * Invert the relationship between rule and visitor to simplify rule creation
     */
    @Delegate final Rule rule = new AbstractAstVisitorRule() {
        String name = GradleLintRule.this.ruleId
        int priority = 0 // not relevant, as this 'rule' will never emit a violation
        Stack<MethodCallExpression> closureStack = new Stack<MethodCallExpression>()

        AbstractAstVisitor gradleAstVisitor = new AbstractAstVisitor() {
            // fall back on some common configurations in case the rule is not GradleModelAware
            Collection<String> configurations = ['archives', 'default', 'compile', 'runtime', 'testCompile', 'testRuntime']

            boolean inDependenciesBlock = false
            boolean inConfigurationsBlock = false

            @Override
            final void visitMethodCallExpression(MethodCallExpression call) {
                def methodName = call.methodAsString

                if (methodName == 'runScript' && project) {
                    configurations = project.configurations.collect { it.name }
                }

                def expressions = call.arguments.expressions
                def objectExpression = call.objectExpression.text

                if (methodName == 'ignore' && objectExpression == 'gradleLint') {
                    rulesToIgnore = expressions.findAll { it instanceof ConstantExpression }.collect { it.text }
                    if (rulesToIgnore.isEmpty())
                        globalIgnoreOn = true

                    super.visitMethodCallExpression(call)
                    GradleLintRule.this.visitMethodCallExpression(call)

                    rulesToIgnore.clear()
                    globalIgnoreOn = false

                    return
                }

                if (inDependenciesBlock) {
                    visitMethodCallInDependencies(call)
                } else if (inConfigurationsBlock) {
                    visitMethodCallInConfigurations(call)
                }

                if (methodName == 'dependencies') {
                    inDependenciesBlock = true
                    super.visitMethodCallExpression(call)
                    inDependenciesBlock = false
                } else if (methodName == 'configurations') {
                    inConfigurationsBlock = true
                    super.visitMethodCallExpression(call)
                    inConfigurationsBlock = false
                } else if (methodName == 'apply') {
                    if (expressions.any { it instanceof MapExpression }) {
                        def entries = GradleAstUtil.collectEntryExpressions(call)
                        if (entries.plugin) {
                            visitApplyPlugin(call, entries.plugin)
                        }
                    }
                } else if (!expressions.isEmpty() && expressions.last() instanceof ClosureExpression) {
                    closureStack.push(call)
                    super.visitMethodCallExpression(call)

                    // because closureStack is state that is shared with GradleLintRule, we need to pre-empt the composite
                    // visitor and call out to the rule now before popping the stack
                    GradleLintRule.this.visitMethodCallExpression(call)

                    closureStack.pop()
                } else {
                    super.visitMethodCallExpression(call)
                }
            }

            @Override
            void visitExpressionStatement(ExpressionStatement statement) {
                def expression = statement.expression
                if (!closureStack.isEmpty()) {
                    def closureName = closureStack.peek().methodAsString

                    if (expression instanceof BinaryExpression) {
                        if (expression.rightExpression instanceof ConstantExpression) { // STYLE: nebula { moduleOwner = 'me' }
                            // if the right side isn't a constant expression, we won't be able to evaluate it through just the AST
                            visitExtensionProperty(statement, closureName, expression.leftExpression.text,
                                    expression.rightExpression.text)
                        }

                        // otherwise, still give a rule the opportunity to check the value of the extension property from a
                        // resolved Gradle model and react accordingly

                        // STYLE: nebula { moduleOwner = trim('me') }
                        visitExtensionProperty(statement, closureName, expression.leftExpression.text)
                    } else if (expression instanceof MethodCallExpression) {
                        if (expression.arguments instanceof ArgumentListExpression) {
                            def args = expression.arguments.expressions as List<Expression>
                            if (args.size() == 1) {
                                if (args[0] instanceof ConstantExpression) { // STYLE: nebula { moduleOwner 'me' }
                                    visitExtensionProperty(statement, closureName, expression.methodAsString, args[0].text)
                                }
                                // STYLE: nebula { moduleOwner trim('me') }
                                visitExtensionProperty(statement, closureName, expression.methodAsString)
                            }
                        }
                    }
                } else if (expression instanceof BinaryExpression && expression.leftExpression instanceof PropertyExpression) {
                    def extension = expression.leftExpression.objectExpression.text
                    def prop = expression.leftExpression.property.text
                    if (expression.rightExpression instanceof ConstantExpression) {
                        // STYLE: nebula.moduleOwner = 'me'
                        visitExtensionProperty(statement, extension, prop, expression.rightExpression.text)
                    }
                    // STYLE: nebula.moduleOwner trim('me')
                    visitExtensionProperty(statement, extension, prop)
                }
                super.visitExpressionStatement(statement)
            }

            private void visitMethodCallInConfigurations(MethodCallExpression call) {
                def methodName = call.methodAsString
                def conf = call.objectExpression.text

                // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/ModuleDependency.html#exclude(java.util.Map)
                if ((configurations.contains(conf) || conf == 'all') && methodName == 'exclude') {
                    def entries = GradleAstUtil.collectEntryExpressions(call)
                    visitConfigurationExclude(call, conf, new GradleDependency(entries.group, entries.module))
                }
            }

            private void visitMethodCallInDependencies(MethodCallExpression call) {
                // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
                def methodName = call.methodAsString
                def args = call.arguments.expressions as List
                if (!args.empty && configurations.contains(methodName)) {
                    if (call.arguments.expressions.any { it instanceof MapExpression }) {
                        def entries = GradleAstUtil.collectEntryExpressions(call)
                        visitGradleDependency(call, methodName, new GradleDependency(
                                entries.group,
                                entries.name,
                                entries.version,
                                entries.classifier,
                                entries.ext,
                                entries.conf,
                                GradleDependency.Syntax.MapNotation))
                    } else if (call.arguments.expressions.any { it instanceof ConstantExpression }) {
                        def expr = call.arguments.expressions.findResult {
                            it instanceof ConstantExpression ? it.value : null
                        }
                        def matcher = expr =~ /(?<group>[^:]+):(?<name>[^:]+):(?<version>[^@:]+)(?<classifier>:[^@]+)?(?<ext>@.+)?/
                        if (matcher.matches()) {
                            visitGradleDependency(call, methodName, new GradleDependency(
                                    matcher.group('group'),
                                    matcher.group('name'),
                                    matcher.group('version'),
                                    matcher.group('classifier'),
                                    matcher.group('ext'),
                                    null,
                                    GradleDependency.Syntax.StringNotation))
                        }
                    }
                }
            }
        }

        @Override
        AstVisitor getAstVisitor() { compositeVisitor }

        /**
         * AST visitor that delegates to both the gradleAstVisitor and the user-defined rule in that order.
         * Keeping them separate helps prevent user-defined visitors from inadvertently breaking the assumptions of
         * gradleAstVisitor
         */
        AstVisitor compositeVisitor = new AbstractAstVisitor() {
            @Override
            List<Violation> getViolations() {
                return GradleLintRule.this.gradleViolations
            }

            void both(Closure c) {
                c(gradleAstVisitor)
                c(GradleLintRule.this)
            }

            @Override
            protected void visitClassEx(ClassNode node) {
                both { it.visitClassEx(node) }
            }

            @Override
            protected void visitClassComplete(ClassNode node) {
                both { it.visitClassComplete(node) }
            }

            @Override
            protected void visitMethodComplete(MethodNode node) {
                both { it.visitMethodComplete(node) }
            }

            @Override
            protected void visitMethodEx(MethodNode node) {
                both { it.visitMethodEx(node) }
            }

            @Override
            protected void visitObjectInitializerStatements(ClassNode node) {
                both { it.visitObjectInitializerStatements(node) }
            }

            @Override
            void visitPackage(PackageNode node) {
                both { it.visitPackage(node) }
            }

            @Override
            void visitImports(ModuleNode node) {
                both { it.visitImports(node) }
            }

            @Override
            void visitAnnotations(AnnotatedNode node) {
                both { it.visitAnnotations(node) }
            }

            @Override
            protected void visitClassCodeContainer(Statement code) {
                both { it.visitClassCodeContainer(code) }
            }

            @Override
            void visitDeclarationExpression(DeclarationExpression expression) {
                both { it.visitDeclarationExpression(expression) }
            }

            @Override
            protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
                both { it.visitConstructorOrMethod(node, isConstructor) }
            }

            @Override
            void visitConstructor(ConstructorNode node) {
                both { it.visitConstructor(node) }
            }

            @Override
            void visitField(FieldNode node) {
                both { it.visitField(node) }
            }

            @Override
            void visitProperty(PropertyNode node) {
                both { it.visitProperty(node) }
            }

            @Override
            protected void visitStatement(Statement statement) {
                both { it.visitStatement(statement) }
            }

            @Override
            void visitAssertStatement(AssertStatement statement) {
                both { it.visitAssertStatement(statement) }
            }

            @Override
            void visitBlockStatement(BlockStatement block) {
                both { it.visitBlockStatement(block) }
            }

            @Override
            void visitBreakStatement(BreakStatement statement) {
                both { it.visitBreakStatement(statement) }
            }

            @Override
            void visitCaseStatement(CaseStatement statement) {
                both { it.visitCaseStatement(statement) }
            }

            @Override
            void visitCatchStatement(CatchStatement statement) {
                both { it.visitCatchStatement(statement) }
            }

            @Override
            void visitContinueStatement(ContinueStatement statement) {
                both { it.visitContinueStatement(statement) }
            }

            @Override
            void visitDoWhileLoop(DoWhileStatement loop) {
                both { it.visitDoWhileLoop(loop) }
            }

            @Override
            void visitExpressionStatement(ExpressionStatement statement) {
                both { it.visitExpressionStatement(statement) }
            }

            @Override
            void visitForLoop(ForStatement forLoop) {
                both { it.visitForLoop(forLoop) }
            }

            @Override
            void visitIfElse(IfStatement ifElse) {
                both { it.visitIfElse(ifElse) }
            }

            @Override
            void visitReturnStatement(ReturnStatement statement) {
                both { it.visitReturnStatement(statement) }
            }

            @Override
            void visitSwitch(SwitchStatement statement) {
                both { it.visitSwitch(statement) }
            }

            @Override
            void visitSynchronizedStatement(SynchronizedStatement statement) {
                both { it.visitSynchronizedStatement(statement) }
            }

            @Override
            void visitThrowStatement(ThrowStatement statement) {
                both { it.visitThrowStatement(statement) }
            }

            @Override
            void visitTryCatchFinally(TryCatchStatement statement) {
                both { it.visitTryCatchFinally(statement) }
            }

            @Override
            void visitWhileLoop(WhileStatement loop) {
                both { it.visitWhileLoop(loop) }
            }

            @Override
            protected void visitEmptyStatement(EmptyStatement statement) {
                both { it.visitEmptyStatement(statement) }
            }

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                both { it.visitMethodCallExpression(call) }
            }

            @Override
            void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
                both { it.visitStaticMethodCallExpression(call) }
            }

            @Override
            void visitConstructorCallExpression(ConstructorCallExpression call) {
                both { it.visitConstructorCallExpression(call) }
            }

            @Override
            void visitBinaryExpression(BinaryExpression expression) {
                both { it.visitBinaryExpression(expression) }
            }

            @Override
            void visitTernaryExpression(TernaryExpression expression) {
                both { it.visitTernaryExpression(expression) }
            }

            @Override
            void visitShortTernaryExpression(ElvisOperatorExpression expression) {
                both { it.visitShortTernaryExpression(expression) }
            }

            @Override
            void visitPostfixExpression(PostfixExpression expression) {
                both { it.visitPostfixExpression(expression) }
            }

            @Override
            void visitPrefixExpression(PrefixExpression expression) {
                both { it.visitPrefixExpression(expression) }
            }

            @Override
            void visitBooleanExpression(BooleanExpression expression) {
                both { it.visitBooleanExpression(expression) }
            }

            @Override
            void visitNotExpression(NotExpression expression) {
                both { it.visitNotExpression(expression) }
            }

            @Override
            void visitClosureExpression(ClosureExpression expression) {
                both { it.visitClosureExpression(expression) }
            }

            @Override
            void visitTupleExpression(TupleExpression expression) {
                both { it.visitTupleExpression(expression) }
            }

            @Override
            void visitListExpression(ListExpression expression) {
                both { it.visitListExpression(expression) }
            }

            @Override
            void visitArrayExpression(ArrayExpression expression) {
                both { it.visitArrayExpression(expression) }
            }

            @Override
            void visitMapExpression(MapExpression expression) {
                both { it.visitMapExpression(expression) }
            }

            @Override
            void visitMapEntryExpression(MapEntryExpression expression) {
                both { it.visitMapEntryExpression(expression) }
            }

            @Override
            void visitRangeExpression(RangeExpression expression) {
                both { it.visitRangeExpression(expression) }
            }

            @Override
            void visitSpreadExpression(SpreadExpression expression) {
                both { it.visitSpreadExpression(expression) }
            }

            @Override
            void visitSpreadMapExpression(SpreadMapExpression expression) {
                both { it.visitSpreadMapExpression(expression) }
            }

            @Override
            void visitMethodPointerExpression(MethodPointerExpression expression) {
                both { it.visitMethodPointerExpression(expression) }
            }

            @Override
            void visitUnaryMinusExpression(UnaryMinusExpression expression) {
                both { it.visitUnaryMinusExpression(expression) }
            }

            @Override
            void visitUnaryPlusExpression(UnaryPlusExpression expression) {
                both { it.visitUnaryPlusExpression(expression) }
            }

            @Override
            void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
                both { it.visitBitwiseNegationExpression(expression) }
            }

            @Override
            void visitCastExpression(CastExpression expression) {
                both { it.visitCastExpression(expression) }
            }

            @Override
            void visitConstantExpression(ConstantExpression expression) {
                both { it.visitConstantExpression(expression) }
            }

            @Override
            void visitClassExpression(ClassExpression expression) {
                both { it.visitClassExpression(expression) }
            }

            @Override
            void visitVariableExpression(VariableExpression expression) {
                both { it.visitVariableExpression(expression) }
            }

            @Override
            void visitPropertyExpression(PropertyExpression expression) {
                both { it.visitPropertyExpression(expression) }
            }

            @Override
            void visitAttributeExpression(AttributeExpression expression) {
                both { it.visitAttributeExpression(expression) }
            }

            @Override
            void visitFieldExpression(FieldExpression expression) {
                both { it.visitFieldExpression(expression) }
            }

            @Override
            void visitGStringExpression(GStringExpression expression) {
                both { it.visitGStringExpression(expression) }
            }

            @Override
            protected void visitListOfExpressions(List<? extends Expression> list) {
                both { it.visitListOfExpressions(list) }
            }

            @Override
            void visitArgumentlistExpression(ArgumentListExpression ale) {
                both { it.visitArgumentlistExpression(ale) }
            }

            @Override
            void visitClosureListExpression(ClosureListExpression cle) {
                both { it.visitClosureListExpression(cle) }
            }

            @Override
            void visitBytecodeExpression(BytecodeExpression cle) {
                both { it.visitBytecodeExpression(cle) }
            }
        }
    }
}

import org.gradle.api.Project
