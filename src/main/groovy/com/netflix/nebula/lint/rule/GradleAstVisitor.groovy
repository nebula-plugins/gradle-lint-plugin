package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement

interface GradleAstVisitor {
    void visitApplyPlugin(MethodCallExpression call, String plugin)

    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep)

    void visitConfigurationExclude(MethodCallExpression call, String conf, GradleDependency exclude)

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
    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop, String value)

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
     */
    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop)

    MethodCallExpression parentClosure()
    List<MethodCallExpression> closureStack()

    void bookmark(String label, ASTNode node)

    ASTNode bookmark(String label)
}