package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.GradleAstUtil
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.control.SourceUnit
import org.gradle.api.Project

/**
 * Groovy AST visitor which searches for `apply from: 'another.gradle'` It takes the file and process it recursively
 */
class AppliedFilesAstVisitor extends ClassCodeVisitorSupport {

    ProjectInfo project
    List<File> appliedFiles = new ArrayList()
    Map<String, String> projectVariablesMapping

    AppliedFilesAstVisitor(Project project) {
        projectVariablesMapping = [
                "\$projectDir" : project.projectDir.toString(),
                "\$project.projectDir" : project.projectDir.toString(),
                "\$rootDir" : project.rootDir.toString(),
                "\$project.rootDir" : project.rootDir.toString(),
        ]
    }
    AppliedFilesAstVisitor(ProjectInfo project) {
        this.project = project
        projectVariablesMapping = [
                "\$projectDir" : project.projectDir.toString(),
                "\$project.projectDir" : project.projectDir.toString(),
                "\$rootDir" : project.rootDir.toString(),
                "\$project.rootDir" : project.rootDir.toString(),
        ]
    }

    void visitApplyFrom(String from) {
        if (! isHttpLink(from)) {
            //handle if path contains ${rootDir} ${project.rootDir} ${projectDir} ${project.projectDir}
            def projectVariable = projectVariablesMapping.find {from.contains(it.key) }
            if (projectVariable) {
                def absolutePath = from.replaceAll("\\$projectVariable.key", projectVariable.value)
                appliedFiles.addAll(SourceCollector.getAllFiles(new File(absolutePath), project))
            } else {
                appliedFiles.addAll(SourceCollector.getAllFiles(new File(project.projectDir, from), project))
            }
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
