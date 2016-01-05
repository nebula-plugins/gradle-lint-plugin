package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.analyzer.CorrectableStringSource
import com.netflix.nebula.lint.plugin.LintRuleRegistry
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codenarc.rule.AbstractAstVisitor
import org.gradle.api.Project

abstract class AbstractGradleLintVisitor extends AbstractAstVisitor {
    boolean isCorrectable() {
        return getSourceCode() instanceof CorrectableStringSource
    }

    CorrectableStringSource getCorrectableSourceCode() {
        return (CorrectableStringSource) getSourceCode()
    }

    Project project
    boolean inDependenciesBlock = false
    boolean inConfigurationsBlock = false

    boolean globalIgnoreOn = false
    List<String> rulesToIgnore = []

    Stack<String> closureStack = new Stack<String>()

    boolean isIgnored() {
        globalIgnoreOn || rulesToIgnore.collect { LintRuleRegistry.findVisitorClassNames(it) }
                .flatten().contains(getClass())
    }

    // fall back on some common configurations in case the rule is not GradleModelAware
    Collection<String> configurations = ['archives', 'default', 'compile', 'runtime', 'testCompile', 'testRuntime']

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

            rulesToIgnore.clear()
            globalIgnoreOn = false

            return
        }

        if (inDependenciesBlock) {
            visitMethodCallInDependencies(call)
        } else if (inConfigurationsBlock) {
            visitMethodCallInConfigurations(call)
        }

        visitMethodCallExpressionInternal(call)

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
                def entries = collectEntryExpressions(call)
                if (entries.plugin) {
                    visitApplyPlugin(call, entries.plugin)
                }
            }
        } else if (!expressions.isEmpty() && expressions.last() instanceof ClosureExpression) {
            closureStack.push(methodName)
            super.visitMethodCallExpression(call)
            closureStack.pop()
        } else {
            super.visitMethodCallExpression(call)
        }
    }

    @Override
    void visitExpressionStatement(ExpressionStatement statement) {
        def expression = statement.expression
        if (!closureStack.isEmpty()) {
            if (expression instanceof BinaryExpression) {
                if (expression.rightExpression instanceof ConstantExpression) { // STYLE: nebula { moduleOwner = 'me' }
                    // if the right side isn't a constant expression, we won't be able to evaluate it through just the AST
                    visitExtensionProperty(statement, closureStack.peek(), expression.leftExpression.text,
                            expression.rightExpression.text)
                }

                // otherwise, still give a rule the opportunity to check the value of the extension property from a
                // resolved Gradle model and react accordingly

                // STYLE: nebula { moduleOwner = trim('me') }
                visitExtensionProperty(statement, closureStack.peek(), expression.leftExpression.text)
            } else if (expression instanceof MethodCallExpression) {
                if (expression.arguments instanceof ArgumentListExpression) {
                    def args = expression.arguments.expressions as List<Expression>
                    if (args.size() == 1) {
                        if (args[0] instanceof ConstantExpression) { // STYLE: nebula { moduleOwner 'me' }
                            visitExtensionProperty(statement, closureStack.peek(), expression.methodAsString, args[0].text)
                        }
                        // STYLE: nebula { moduleOwner trim('me') }
                        visitExtensionProperty(statement, closureStack.peek(), expression.methodAsString)
                    }
                }
            }
        } else if (expression instanceof BinaryExpression && expression.leftExpression instanceof PropertyExpression) {
            def extension = expression.leftExpression.objectExpression.text
            def prop = expression.leftExpression.property.text
            if (expression.rightExpression instanceof ConstantExpression) {
                visitExtensionProperty(statement, extension, prop, expression.rightExpression.text)
            }
            visitExtensionProperty(statement, extension, prop)
        }
        super.visitExpressionStatement(statement)
    }

    private void visitMethodCallInConfigurations(MethodCallExpression call) {
        def methodName = call.methodAsString
        def conf = call.objectExpression.text

        // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/ModuleDependency.html#exclude(java.util.Map)
        if ((configurations.contains(conf) || conf == 'all') && methodName == 'exclude') {
            def entries = collectEntryExpressions(call)
            visitConfigurationExclude(call, conf, new GradleDependency(entries.group, entries.module))
        }
    }

    private void visitMethodCallInDependencies(MethodCallExpression call) {
        // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
        def methodName = call.methodAsString
        def args = call.arguments.expressions as List
        if (!args.empty && configurations.contains(methodName)) {
            if (call.arguments.expressions.any { it instanceof MapExpression }) {
                def entries = collectEntryExpressions(call)
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

    @SuppressWarnings("GrDeprecatedAPIUsage")
    @Override
    protected final void addViolation(ASTNode node) {
        super.addViolation(node)
    }

    @Override
    protected final void addViolation(ASTNode node, String message) {
        super.addViolation(node, message)
    }

    void addViolationWithReplacement(ASTNode node, String message, String replacement) {
        if (isIgnored())
            return

        def v = new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message,
                replacement: replacement)
        violations.add(v)
        if (replacement != null && isCorrectable())
            correctableSourceCode.replace(node, replacement)
    }

    void addViolationToDelete(ASTNode node, String message) {
        if (isIgnored())
            return

        def v = new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message,
                shouldDelete: true)
        violations.add(v)
        if (isCorrectable())
            correctableSourceCode.delete(node)
    }

    void addViolationNoCorrection(ASTNode node, String message) {
        if (isIgnored())
            return

        def v = new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message)
        violations.add(v)
    }

    /**
     * @param node
     * @return a single or multi-line code snippet stripped of indentation, code that exists on the starting line
     * prior to the starting column, and code that exists on the last line after the ending column
     */
    private String formattedViolation(ASTNode node) {
        // make a copy of violating lines so they can be formatted for display in a report
        def violatingLines = new ArrayList(sourceCode.lines.subList(node.lineNumber - 1, node.lastLineNumber))

        violatingLines[0] = violatingLines[0][(node.columnNumber - 1)..-1]
        if (node.lineNumber != node.lastLineNumber) {
            violatingLines[-1] = violatingLines[-1][0..(node.lastColumnNumber - 2)]
        }

        violatingLines.eachWithIndex { String line, Integer i ->
            if (i > 0) violatingLines[i] = '  ' + line.stripIndent()
        }

        violatingLines.join('\n').stripIndent()
    }

    protected void visitMethodCallExpressionInternal(MethodCallExpression call) {}

    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {}

    void visitApplyPlugin(MethodCallExpression call, String plugin) {}

    void visitConfigurationExclude(MethodCallExpression call, String conf, GradleDependency exclude) {}

    /**
     * Visit potential extension properties.  Because of the ambiguity inherent in the shared DSL
     * syntax for internal Gradle Handler objects (e.g. DependencyHandler) and extension objects,
     * this method will be triggered for method calls and property assignments on Handlers as well.
     * As long as you are overriding this visitor to look for a specific extension name and property,
     * this ambiguity will not cause problems.
     *
     * @param expression - a MethodCallExpression or BinaryExpression
     * @param extension - extension object name as rendered in the DSL
     * @param prop - property target on the extension object
     * @param value - value to assign to the extension property
     */
    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop, String value) {}

    /**
     * Visit potential extension properties.  Because of the ambiguity inherent in the shared DSL
     * syntax for internal Gradle Handler objects (e.g. DependencyHandler) and extension objects,
     * this method will be triggered for method calls and property assignments on Handlers as well.
     * As long as you are overriding this visitor to look for a specific extension name and property,
     * this ambiguity will not cause problems.
     *
     * @param expression - a MethodCallExpression or BinaryExpression
     * @param extension - extension object name as rendered in the DSL
     * @param prop - property target on the extension object
     * @param value - value to assign to the extension property
     */
    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop) {}

    protected static Map<String, String> collectEntryExpressions(MethodCallExpression call) {
        call.arguments.expressions
                .findAll { it instanceof MapExpression }
                .collect { it.mapEntryExpressions }
                .flatten()
                .collectEntries { [it.keyExpression.text, it.valueExpression.text] } as Map<String, String>
    }
}
