package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.analyzer.CorrectableStringSource
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
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

    // fall back on some common configurations in case the rule is not GradleModelAware
    Collection<String> configurations = ['archives', 'default', 'compile', 'runtime', 'testCompile', 'testRuntime']

    @Override
    final void visitMethodCallExpression(MethodCallExpression call) {
        def methodName = call.methodAsString

        if(methodName == 'runScript' && project) {
            configurations = project.configurations.collect { it.name }
        }

        if (inDependenciesBlock) {
            visitMethodCallInDependencies(call)
        } else if(inConfigurationsBlock) {
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
            if (call.arguments.expressions.any { it instanceof MapExpression }) {
                def entries = collectEntryExpressions(call)
                if (entries.plugin) {
                    visitApplyPlugin(call, entries.plugin)
                }
            }
        } else {
            super.visitMethodCallExpression(call)
        }
    }

    private void visitMethodCallInConfigurations(MethodCallExpression call) {
        def methodName = call.methodAsString
        def conf = call.objectExpression.text

        // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/ModuleDependency.html#exclude(java.util.Map)
        if((configurations.contains(conf) || conf == 'all') && methodName == 'exclude') {
            def entries = collectEntryExpressions(call)
            visitConfigurationExclude(call, conf, new GradleDependency(entries.group, entries.module))
        }
    }

    private void visitMethodCallInDependencies(MethodCallExpression call) {
        // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/dsl/DependencyHandler.html
        def methodName = call.methodAsString
        def args = call.arguments.expressions as List
        if (!args.empty && !(args[-1] instanceof ClosureExpression)) {
            if (configurations.contains(methodName)) {
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
                } else if (!call.arguments.expressions.find { !(it instanceof ConstantExpression) }) {
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

    GradleViolation addViolationWithReplacement(ASTNode node, String message, String replacement) {
        def v = new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message,
                replacement: replacement)
        violations.add(v)
        if (replacement != null && isCorrectable())
            correctableSourceCode.replace(node, replacement)
        v
    }

    GradleViolation addViolationToDelete(ASTNode node, String message) {
        def v = new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message,
                shouldDelete: true)
        violations.add(v)
        if (isCorrectable())
            correctableSourceCode.delete(node)
        v
    }

    GradleViolation addViolationNoCorrection(ASTNode node, String message) {
        def v = new GradleViolation(rule: rule, lineNumber: node.lineNumber,
                sourceLine: formattedViolation(node), message: message)
        violations.add(v)
        v
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

    private static Map<String, String> collectEntryExpressions(MethodCallExpression call) {
        call.arguments.expressions
                .findAll { it instanceof MapExpression }
                .collect { it.mapEntryExpressions }
                .flatten()
                .collectEntries { [it.keyExpression.text, it.valueExpression.text] } as Map<String, String>
    }
}
