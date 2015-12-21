package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.rule.AstVisitor

class AbstractGradleLintVisitorSpec extends AbstractRuleSpec {
    def 'parse dependency map syntax'() {
        when:
        def rule = new SimpleLintRule()
        runRulesAgainst("""
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11'
            }
        """, rule)
        def visited = rule.visitor.visitedDeps[0]

        then:
        visited.group == 'junit'
        visited.name == 'junit'
        visited.version == '4.11'
        visited.syntax == GradleDependency.Syntax.MapNotation
    }

    def 'parse dependency string syntax'() {
        when:
        def rule = new SimpleLintRule()
        runRulesAgainst("""
            dependencies {
               compile 'junit:junit:4.11'
            }
        """, rule)
        def visited = rule.visitor.visitedDeps[0]

        then:
        visited.group == 'junit'
        visited.name == 'junit'
        visited.version == '4.11'
        visited.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'apply plugin'() {
        when:
        def rule = new SimpleLintRule()
        runRulesAgainst("""
            apply plugin: 'nebula-dependency-lock'
        """, rule)
        def plugins = rule.visitor.appliedPlugins

        then:
        plugins == ['nebula-dependency-lock']
    }

    static class SimpleLintVisitor extends AbstractGradleLintVisitor {
        List<GradleDependency> visitedDeps = []
        List<String> appliedPlugins = []

        @Override
        void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
            visitedDeps += dep
        }

        @Override
        void visitApplyPlugin(MethodCallExpression call, String plugin) {
            appliedPlugins += plugin
        }
    }

    static class SimpleLintRule extends AbstractAstVisitorRule {
        String name = 'SimpleLintRule'
        int priority = 3
        SimpleLintVisitor visitor = new SimpleLintVisitor()

        @Override
        AstVisitor getAstVisitor() { visitor }
    }
}