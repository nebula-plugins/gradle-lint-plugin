package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleDependency
import org.codehaus.groovy.ast.expr.MethodCallExpression

class DependencyViolationUtil {

    static void replaceProjectDependencyConfiguration(GradleViolation violation, MethodCallExpression call, String configuration, String project) {
        violation.replaceWith(call, "$configuration project('$project')")
    }

    static void replaceDependencyConfiguration(GradleViolation violation, MethodCallExpression call, String conf, GradleDependency dep) {
        violation.replaceWith(call, "$conf '${dep.toNotation()}'")
    }

    static void replaceDependencyConfiguration(GradleViolation violation, MethodCallExpression call, String conf) {
        List<String> lines = violation.files.text.readLines()
        List<String> closureLines = lines.subList(call.lineNumber-1, call.lastLineNumber)
        String codeBlock = closureLines.join('\n').trim()
        violation.replaceWith(call, codeBlock.replace(call.methodAsString, conf))
    }
}
