package com.netflix.nebula.lint.rule.dsl

import com.netflix.nebula.lint.rule.BuildFiles
import com.netflix.nebula.lint.rule.ModelAwareGradleLintRule
import com.netflix.nebula.lint.utils.IndentUtils
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

class SpaceAssignmentRule extends ModelAwareGradleLintRule {

    String description = "space-assignment syntax is deprecated"

    static final Set<String> knownDeprecatedAssignments = [
            'group',
            'version',
            'status',
            'buildDir',
            'sourceCompatibility',
            'targetCompatibility',
            'url',
            'name',
            'description',
            'tempDir',
            'distributionPath',
    ]

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(dslStack().contains("plugins")) {
            return
        }
        if(call.methodAsString == 'group' && !isGradleGroup(call)) {
            return
        }
        if (call.arguments.size() != 1 || call.arguments[-1] instanceof ClosureExpression) {
            return
        }

        def invokedMethodName = call.methodAsString
        if (!knownDeprecatedAssignments.contains(invokedMethodName)) return

        addViolation(call)
    }

    private boolean isGradleGroup(MethodCallExpression call) {
        if(call.methodAsString != 'group') {
            return false
        }

        return dslStack().empty ||
                dslStack().containsAll(['subprojects']) ||
                dslStack().containsAll(['allprojects']) ||
                dslStack().contains('configureEach')
    }

    private void addViolation(MethodCallExpression call) {
        BuildFiles.Original originalFile = buildFiles.original(call.lineNumber)
        String replacement = IndentUtils.indentText(call, getReplacement(call))
        addBuildLintViolation(description, call)
                .insertBefore(call, replacement)
                .deleteLines(originalFile.file, originalFile.line..originalFile.line)
    }

    private String getReplacement(MethodCallExpression call){
        def originalLine = getSourceCode().line(call.lineNumber-1)
        return originalLine.replaceFirst(call.methodAsString, call.methodAsString + " =")
    }
}
