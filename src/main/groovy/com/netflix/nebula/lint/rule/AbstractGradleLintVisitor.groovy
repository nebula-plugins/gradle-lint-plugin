package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.analyzer.CorrectableStringSource
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitor

abstract class AbstractGradleLintVisitor extends AbstractAstVisitor {
    boolean isCorrectable() {
        return getSourceCode() instanceof CorrectableStringSource
    }

    CorrectableStringSource getCorrectableSourceCode() {
        return (CorrectableStringSource) getSourceCode()
    }

    boolean inDependenciesBlock = false
    List<String> configurations = ['compile', 'runtime', 'testCompile', 'testRuntime']

    @Override
    final void visitMethodCallExpression(MethodCallExpression call) {
        if(inDependenciesBlock) {
            // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
            def args = call.arguments.expressions as List
            if(!args.empty && !(args[-1] instanceof ClosureExpression)) {
                if(configurations.contains(call.methodAsString)) {
                    if(call.arguments.expressions.any { it instanceof MapExpression }) {
                        def entries = collectEntryExpressions(call)
                        visitGradleDependency(call, call.methodAsString, new GradleDependency(
                                entries.group,
                                entries.name,
                                entries.version,
                                entries.classifier,
                                entries.ext,
                                entries.conf,
                                GradleDependency.Syntax.MapNotation))
                    }
                    else if(!call.arguments.expressions.find { !(it instanceof ConstantExpression) }) {
                        def expr = call.arguments.expressions.findResult { it instanceof ConstantExpression ? it.value : null }
                        def matcher = expr =~ /(?<group>[^:]+):(?<name>[^:]+):(?<version>[^@:]+)(?<classifier>:[^@]+)?(?<ext>@.+)?/
                        if(matcher.matches()) {
                            visitGradleDependency(call, call.methodAsString, new GradleDependency(
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

        visitMethodCallExpressionInternal(call)

        if(call.methodAsString == 'dependencies') {
            inDependenciesBlock = true
            super.visitMethodCallExpression(call)
            inDependenciesBlock = false
        }
        else if(call.methodAsString == 'apply') {
            if(call.arguments.expressions.any { it instanceof MapExpression }) {
                def entries = collectEntryExpressions(call)
                if(entries.plugin) {
                    visitApplyPlugin(call, entries.plugin)
                }
            }
        }
        else {
            super.visitMethodCallExpression(call)
        }
    }

    void addViolationWithReplacement(ASTNode node, String message, String replacement = null) {
        violations.add(new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message,
                replacement: replacement))

        if(replacement != null && isCorrectable()) {
            correctableSourceCode.replace(node, replacement)
        }
    }

    void addViolationToDelete(ASTNode node, String message) {
        violations.add(new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message,
                shouldDelete: true))

        if(isCorrectable()) {
            correctableSourceCode.delete(node)
        }
    }

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

    void visitApplyPlugin(MethodCallExpression call, String plugin) { }

    private static Map<String, String> collectEntryExpressions(MethodCallExpression call) {
        call.arguments.expressions
                .findAll { it instanceof MapExpression }
                .collect { it.mapEntryExpressions }
                .flatten()
                .collectEntries { [it.keyExpression.text, it.valueExpression.text] } as Map<String, String>
    }
}
