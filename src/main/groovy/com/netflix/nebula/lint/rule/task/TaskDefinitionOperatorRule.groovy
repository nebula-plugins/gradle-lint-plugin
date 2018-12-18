package com.netflix.nebula.lint.rule.task


import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleLintRule
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression

class TaskDefinitionOperatorRule extends GradleLintRule {

    private final String TASK_DO_LAST = 'doLast'
    private final String TASK_INDICATOR = 'task'
    private final String TASK_OPERATOR = '<<'
    private final String OPENING_BLACKET = '{'
    private final String CLOSING_BRACKET = '}'
    private final String EMPTY_STRING = ''

    String description = "The $TASK_OPERATOR operator was deprecated. Need to use doLast method"

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if(call.methodAsString == TASK_INDICATOR) {
            BinaryExpression binaryExpression = call.arguments.expressions.find { it instanceof BinaryExpression } as BinaryExpression
            if(binaryExpression && binaryExpression.operation.text == TASK_OPERATOR){
                GradleViolation violation = addBuildLintViolation("The $TASK_OPERATOR operator was deprecated. Need to use doLast method", call)
                if(binaryExpression.leftExpression instanceof VariableExpression) {
                    String changes = changeTaskDeclaration(call, binaryExpression, violation)
                    violation.replaceWith(call, changes)
                }
            }
        }
    }

    private String changeTaskDeclaration(MethodCallExpression call, BinaryExpression binaryExpression, GradleViolation violation) {
        String taskName = binaryExpression.leftExpression.variable
        String indent = ' ' * (call.columnNumber - 1)
        String changes = "$TASK_INDICATOR $taskName {\n" +
                "    $indent$TASK_DO_LAST $OPENING_BLACKET\n"
        List<String> lines = violation.files.text.readLines()
        List<String> closureLines = lines.subList(call.lineNumber-1, call.lastLineNumber)
        List<String> indentedLines = closureLines.collect { "    $it" }
        String originalClosure = indentedLines.join('\n')
        changes +=  extractClosureCodeBlock(taskName, originalClosure)
        changes += "\n    $indent$CLOSING_BRACKET\n$indent$CLOSING_BRACKET"
        return changes
    }

    private String extractClosureCodeBlock(String taskName, String originalClosure) {
        String codeBlock = originalClosure.replace(TASK_INDICATOR, EMPTY_STRING)
                .replace(taskName, EMPTY_STRING)
                .replace(TASK_OPERATOR, EMPTY_STRING)

        codeBlock = StringUtils.replaceOnce(codeBlock, OPENING_BLACKET, EMPTY_STRING)

        int closingBracketIndex = codeBlock.lastIndexOf(CLOSING_BRACKET)
        if(closingBracketIndex > 0)
            codeBlock = codeBlock.substring(0, closingBracketIndex)

        return codeBlock
    }
}
