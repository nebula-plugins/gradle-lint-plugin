package com.netflix.nebula.lint.rule.rename

import com.netflix.nebula.lint.rule.AbstractGradleLintVisitor
import groovy.transform.Canonical
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.rule.AstVisitor

@Canonical
class PluginRenamedRule extends AbstractAstVisitorRule {
    String name
    String deprecatedPluginName
    String pluginName
    int priority = 2

    @Override
    AstVisitor getAstVisitor() {
        return new PluginRenamedAstVisitor(deprecatedPluginName, pluginName)
    }
}

@Canonical
class PluginRenamedAstVisitor extends AbstractGradleLintVisitor {
    String deprecatedPluginName
    String pluginName

    @Override
    void visitApplyPlugin(MethodCallExpression call, String plugin) {
        if(plugin == deprecatedPluginName) {
            addViolation(call, "plugin $deprecatedPluginName has been renamed to $pluginName")
            if(isCorrectable()) {
                correctableSourceCode.replace(call, "apply plugin: '$pluginName'")
            }
        }
    }
}
