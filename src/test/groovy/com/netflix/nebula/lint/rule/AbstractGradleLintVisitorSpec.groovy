package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.rule.AstVisitor

class AbstractGradleLintVisitorSpec extends AbstractRuleSpec {
    def 'parse dependency map syntax'() {
        when:
        project.buildFile << """
            dependencies {
               compile group: 'junit', name: 'junit', version: '4.11'
            }
        """

        def rule = new SimpleLintRule()
        runRulesAgainst(rule)
        def visited = rule.visitor.visitedDeps[0]

        then:
        visited.group == 'junit'
        visited.name == 'junit'
        visited.version == '4.11'
        visited.syntax == GradleDependency.Syntax.MapNotation
    }

    def 'visit dependencies defined inside subprojects block'() {
        when:
        project.buildFile << """
            subprojects {
                dependencies {
                   compile 'junit:junit:4.11'
                }
            }
        """

        def rule = new SimpleLintRule()
        runRulesAgainst(rule)

        def visited = rule.visitor.visitedDeps[0]

        then:
        visited.group == 'junit'
        visited.name == 'junit'
        visited.version == '4.11'
        visited.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'parse dependency string syntax'() {
        when:
        project.buildFile << """
            dependencies {
               compile 'junit:junit:4.11'
            }
        """

        def rule = new SimpleLintRule()
        runRulesAgainst(rule)
        def visited = rule.visitor.visitedDeps[0]

        then:
        visited.group == 'junit'
        visited.name == 'junit'
        visited.version == '4.11'
        visited.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'apply plugin'() {
        when:
        project.buildFile << """
            apply plugin: 'nebula-dependency-lock'
        """

        def rule = new SimpleLintRule()
        runRulesAgainst(rule)
        def plugins = rule.visitor.appliedPlugins

        then:
        plugins == ['nebula-dependency-lock']
    }

    def 'add violation with deletion'() {
        when:
        def rule = new AbstractAstVisitorRule() {
            String name = 'no-apply-plugin'
            int priority = 2

            @Override
            AstVisitor getAstVisitor() {
                return new AbstractGradleLintVisitor() {
                    @Override
                    void visitApplyPlugin(MethodCallExpression call, String plugin) {
                        addViolationToDelete(call, "'apply plugin' syntax is not allowed")
                    }
                }
            }
        }

        project.buildFile << "apply plugin: 'java'"

        then:
        correct(rule) == ''
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