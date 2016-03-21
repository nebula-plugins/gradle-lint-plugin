package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.plugin.GradleLintPlugin
import com.netflix.nebula.lint.plugin.LintRuleRegistry
import com.netflix.nebula.lint.rule.test.AbstractRuleSpec
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.gradle.api.plugins.JavaPlugin
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Unroll

class GradleLintRuleSpec extends AbstractRuleSpec {
    @Rule
    TemporaryFolder temp

    def 'visit `apply plugin`'() {
        when:
        project.buildFile << '''
            apply plugin: 'java'
        '''

        def pluginCount = 0

        runRulesAgainst(new GradleLintRule() {
            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                pluginCount++
            }
        })

        then:
        pluginCount == 1
    }

    def 'visit dependencies'() {
        when:
        project.buildFile << """
            dependencies {
               compile group: 'a', name: 'a', version: '1'
            }

            subprojects {
                dependencies {
                   compile 'b:b:1'
                }
            }
        """

        def visited = []

        runRulesAgainst(new GradleLintRule() {
            @Override
            void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
                visited += dep
            }
        })

        def a = visited.find { it.name == 'a' }
        def b = visited.find { it.name == 'b' }

        then:
        a
        a.group == 'a'
        a.version == '1'
        a.syntax == GradleDependency.Syntax.MapNotation

        then:
        b
        b.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'add violation with deletion'() {
        when:
        project.buildFile << "apply plugin: 'java'"

        def rule = new GradleLintRule() {
            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                addViolationToDelete(call, "'apply plugin' syntax is not allowed")
            }
        }

        then:
        correct(rule) == ''
    }

    def 'add violation with multiple insertions'() {
        when:
        project.buildFile << """
            apply plugin: 'java'

            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """.stripIndent()

        def rule = new GradleLintRule() {
            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                bookmark('lastApplyPlugin', call)
            }

            @Override
            void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
                if(bookmark('lastApplyPlugin')) {
                    addViolationInsert(call, 'should generate source jar', "\napply plugin: 'nebula.source-jar'", bookmark('lastApplyPlugin'))
                    addViolationInsert(call, 'should generate javadoc jar', "\napply plugin: 'nebula.javadoc-jar'", bookmark('lastApplyPlugin'))
                }
            }
        }

        then:
        correct(rule) == """
            apply plugin: 'java'
            apply plugin: 'nebula.source-jar'
            apply plugin: 'nebula.javadoc-jar'

            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """.stripIndent()
    }

    @Unroll
    def 'violations suppression inside of ignore blocks when ignored rule(s) is `#rules`'() {
        setup:
        def rule = new GradleLintRule() {
            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                addViolationNoCorrection(call, 'no plugins allowed')
            }
        }
        rule.ruleId = 'no-plugins-allowed'

        new File(temp.root, 'META-INF/lint-rules').mkdirs()
        def noPluginsProp = temp.newFile("META-INF/lint-rules/no-plugins-allowed.properties")
        noPluginsProp << "implementation-class=${rule.class.name}"
        LintRuleRegistry.classLoader = new URLClassLoader([temp.root.toURI().toURL()] as URL[], getClass().getClassLoader())

        when:
        project.buildFile << """
            gradleLint.ignore($rules) { apply plugin: 'java' }
        """

        def result = runRulesAgainst(rule)

        then:
        result.violates() == violates

        where:
        rules                               |  violates
        /'no-plugins-allowed'/              |  false
        /'other-rule'/                      |  true
        /'no-plugins-allowed','other-rule'/ |  false
        ''                                  |  false
    }

    def 'ignore closure properly delegates'() {
        when:
        project.with {
            plugins.apply(JavaPlugin)
            plugins.apply(GradleLintPlugin)
            dependencies {
                gradleLint.ignore {
                    compile 'com.google.guava:guava:19.0'
                }
            }
        }

        then:
        project.configurations.compile.dependencies.any { it.name == 'guava' }
    }

    def 'visit extension properties'() {
        when:
        project.buildFile << """
            nebula {
                moduleOwner = 'me'
            }

            nebula.moduleOwner = 'me'

            subprojects {
                nebula {
                    moduleOwner = 'me'
                }
            }

            allprojects {
                nebula {
                    moduleOwner 'me' // sometimes this shorthand syntax is provided, notice no '='
                }
            }
        """

        def rule = new GradleLintRule() {
            @Override
            void visitExtensionProperty(ExpressionStatement expression, String extension, String prop) {
                if(extension == 'nebula' && prop == 'moduleOwner')
                    addViolationToDelete(expression, 'moduleOwner is deprecated and should be removed')
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 4
    }

    def 'codenarc visit methods in a rule have access to parent closure'() {
        when:
        project.buildFile << """
            publications {
                JAR
            }
        """

        MethodCallExpression parent = null

        runRulesAgainst(new GradleLintRule() {
            @Override
            void visitExpressionStatement(ExpressionStatement statement) {
                if(statement.expression instanceof VariableExpression)
                    parent = parentClosure()
            }
        })

        then:
        parent?.methodAsString == 'publications'
    }

    def 'format multi-line violations'() {
        when:
        project.buildFile << """
            multiline {
              'this is a multiline'
            }
        """

        def results = runRulesAgainst(new GradleLintRule() {
            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                if(call.methodAsString == 'multiline')
                    addViolationToDelete(call, 'this block can be deleted')
            }
        })

        then:
        (results.violations[0] as GradleViolation).sourceLine == '''
            multiline {
              'this is a multiline'
            }
        '''.stripIndent().trim()
    }
}