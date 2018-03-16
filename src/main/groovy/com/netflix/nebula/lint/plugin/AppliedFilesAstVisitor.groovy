package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.GradleAstUtil
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.SourceUnit

/**
 * Groovy AST visitor which searches for `apply from: 'another.gradle'` It takes the file and process it recursively
 */
class AppliedFilesAstVisitor extends ClassCodeVisitorSupport {

    File projectDir
    List<File> appliedFiles = new ArrayList()

    AppliedFilesAstVisitor(File projectDir) {
        this.projectDir = projectDir
    }

    void visitApplyFrom(String from) {
        if (! isHttpLink(from)) {
            appliedFiles.addAll(SourceCollector.getAllFiles(new File(projectDir, from), projectDir))
        }
    }

    boolean isHttpLink(String from) {
        from.toLowerCase().startsWith("http")
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        super.visitMethodCallExpression(call)
        def methodName = call.methodAsString
        def expressions = call.arguments.expressions
        def objectExpression = call.objectExpression.text

        if (methodName == 'ignore' && objectExpression == 'gradleLint') {
            return // short-circuit ignore calls
        }

        if (methodName == 'apply') {
            if (expressions.any { it instanceof MapExpression }) {
                def entries = GradleAstUtil.collectEntryExpressions(call)
                if (entries.from) {
                    visitApplyFrom(entries.from)
                }
            }
        }
    }

    @Override
    protected SourceUnit getSourceUnit() {
        throw new RuntimeException("should never be called")
    }
}
