/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.plugin.GradleLintPlugin
import com.netflix.nebula.lint.plugin.LintRuleRegistry
import com.netflix.nebula.lint.rule.test.AbstractRuleSpec
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.gradle.api.plugins.JavaPlugin
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Ignore
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

    @Ignore("Fails due to JGit bug. Can be re-instated once https://git.eclipse.org/r/#/c/70797/ is merged")
    def 'add violation with deletion'() {
        when:
        project.buildFile << "apply plugin: 'java'"

        def rule = new GradleLintRule() {
            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                addBuildLintViolation("'apply plugin' syntax is not allowed", call).delete(call)
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
                    addBuildLintViolation('should generate source jar', call)
                        .insertAfter(bookmark('lastApplyPlugin'), "apply plugin: 'nebula.source-jar'")
                    addBuildLintViolation('should generate javadoc jar', call)
                        .insertAfter(bookmark('lastApplyPlugin'), "apply plugin: 'nebula.javadoc-jar'")
                }
            }
        }

        then:
        correct(rule) == """
            apply plugin: 'java'
            apply plugin: 'nebula.javadoc-jar'
            apply plugin: 'nebula.source-jar'

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
                addBuildLintViolation('no plugins allowed', call)
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
                    addBuildLintViolation('moduleOwner is deprecated and should be removed', expression)
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
                    addBuildLintViolation('this block can be deleted', call).delete(call)
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