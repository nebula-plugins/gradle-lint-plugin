/*
 * Copyright 2015-2019 Netflix, Inc.
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
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codenarc.analyzer.StringSourceAnalyzer
import org.gradle.api.plugins.JavaPlugin
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
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
            String description = 'test'

            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                pluginCount++
            }
        })

        then:
        pluginCount == 1
    }

    @Unroll
    def 'visit `plugins` - #plugin'() {
        when:
        project.buildFile << """
            plugins {
             id '${plugin}'
            }
        """

        def pluginCount = 0

        runRulesAgainst(new GradleLintRule() {
            String description = 'test'

            @Override
            void visitGradlePlugin(MethodCallExpression call, String conf, GradlePlugin gradlePluginGradleLintRuleSpec) {
                pluginCount++
            }
        })

        then:
        pluginCount == 1

        where:
        plugin << ['java', 'org.gradle.java']
    }

    @Unroll
    def 'visit `plugins` with apply - #plugin'() {
        when:
        project.buildFile << """
            plugins {
             id '${plugin}' apply false
            }
        """

        def pluginCount = 0

        runRulesAgainst(new GradleLintRule() {
            String description = 'test'

            @Override
            void visitGradlePlugin(MethodCallExpression call, String conf, GradlePlugin gradlePluginGradleLintRuleSpec) {
                if(call.methodAsString == 'apply') {
                    pluginCount++
                }
            }
        })

        then:
        pluginCount == 1

        where:
        plugin << ['java', 'org.gradle.java']
    }

    def 'skip nested `plugins`'() {
        when:
        project.buildFile << """
            task test {
              plugins {
             
              }
            }
        """

        def pluginCount = 0

        runRulesAgainst(new GradleLintRule() {
            String description = 'test'

            @Override
            void visitPlugins(MethodCallExpression call) {
                pluginCount++
            }
        })

        then:
        pluginCount == 0
    }

    def 'visit `task`'() {
        when:
        project.buildFile << '''
            task(t1)
            task('t2')
            task(t3) {}
            task('t4') {}
            task t5
            task t6 {}
            task (t7,type: Wrapper)
            task ('t8',type: Wrapper)
            task t9(type: Wrapper)
            task t10(type: Wrapper) {}
            task([:], t11)
            task([type: Wrapper], t12)
            task([type: Wrapper], t13) {}
            tasks.create([name: 't14'])
            tasks.create([name: 't15']) {}
            tasks.create('t16') {}
            tasks.create('t17')
            tasks.create('t18', Wrapper) {}
            tasks.create('t19', Wrapper.class)
        '''

        def taskCount = 0
        def calls = []
        runRulesAgainst(new GradleLintRule() {
            String description = 'test'

            @Override
            void visitTask(MethodCallExpression call, String name, Map<String, String> args) {
                calls[taskCount] = [name: name, args: args]
                taskCount++
            }
        })

        then:
        taskCount == 19
        calls[0] == [name: 't1', args: [:]]
        calls[1] == [name: 't2', args: [:]]
        calls[2] == [name: 't3', args: [:]]
        calls[3] == [name: 't4', args: [:]]
        calls[4] == [name: 't5', args: [:]]
        calls[5] == [name: 't6', args: [:]]
        calls[6] == [name: 't7', args: [type: 'Wrapper']]
        calls[7] == [name: 't8', args: [type: 'Wrapper']]
        calls[8] == [name: 't9', args: [type: 'Wrapper']]
        calls[9] == [name: 't10', args: [type: 'Wrapper']]
        calls[10] == [name: 't11', args: [:]]
        calls[11] == [name: 't12', args: [type: 'Wrapper']]
        calls[12] == [name: 't13', args: [type: 'Wrapper']]
        calls[13] == [name: 't14', args: [name: 't14']]
        calls[14] == [name: 't15', args: [name: 't15']]
        calls[15] == [name: 't16', args: [:]]
        calls[16] == [name: 't17', args: [:]]
        calls[17] == [name: 't18', args: [type: 'Wrapper']]
        calls[18] == [name: 't19', args: [type: 'Wrapper']]
        calls.size() == taskCount
    }

    def 'visit dependencies in buildscript'() {
        when:
        project.buildFile << """
            buildscript {
                repositories {
                    maven { url 'https://plugins.gradle.org/m2/' }
                }

                dependencies {
                    classpath 'com.gradle:build-scan-plugin:1.1.1'
                }
            }
        """

        def dep = new DependencyVisitingRule().run().buildscriptDeps.first()

        then:
        dep.name == 'build-scan-plugin'
    }

    def 'visit dependencies in allprojects block'() {
        when:
        project.buildFile << """
            allprojects {
                apply plugin: 'java'
            
                dependencies {
                   compile 'b:b:1'
                }
            }
        """

        def b = new DependencyVisitingRule().run().allprojectDeps.find { it.name == 'b' }

        then:
        b
        b.syntax == GradleDependency.Syntax.StringNotation
    }



    def 'visit dependencies in subprojects block'() {
        when:
        def subproject = addSubproject('test')
        subproject.configurations.create('compile')
        project.subprojects.add(subproject)
        project.buildFile << """
            subprojects {
                dependencies {
                   compile 'b:b:1'
                }
            }
        """

        def b = new DependencyVisitingRule().run().subprojectDeps.find { it.name == 'b' }

        then:
        b
        b.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'visit dependencies in a project path subprojects block'() {
        when:
        def subproject = addSubproject('test')
        subproject.configurations.create('compile')
        project.subprojects.add(subproject)
        project.buildFile << """
            subprojects(':test') {
                dependencies {
                   compile 'b:b:1'
                }
            }
        """

        def b = new DependencyVisitingRule().run().subprojectDeps.find { it.name == 'b' }

        then:
        b
        b.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'visit dependencies on submodule from sibling'() {
        given:
        def library = addSubproject('library')
        def app = addSubproject('app')
        library.configurations.create('compile')
        app.configurations.create('compile')
        project.subprojects.add(library)
        project.subprojects.add(app)
        new File(project.projectDir, "app").mkdirs()
        app.buildFile << """
            dependencies {
               compile project(":library")
            }
        """
        def rule = new DependencyVisitingRule()

        when:
        new StringSourceAnalyzer(app.buildFile.text).analyze(configureRuleSet(app, configureBuildFile(app, rule)))
        def foundDependencies = rule.submoduleDependencies

        then:
        foundDependencies.size() == 1
        foundDependencies[0] == ':library'
    }

    def 'visit dependencies on submodule from root'() {
        given:
        def library = addSubproject('library')
        def library2 = addSubproject('library2')
        def library3 = addSubproject('library3')
        library.configurations.create('compile')
        project.subprojects.add(library)
        project.subprojects.add(library2)
        project.subprojects.add(library3)
        project.buildFile << """
            def baseName = ":library"

            dependencies {
               compile project(":library")
               compile project(path: ":library2")
               compile project("\${baseName}3")
            }
        """

        when:
        def foundDependencies = new DependencyVisitingRule().run().submoduleDependencies

        then:
        foundDependencies.size() == 3
        foundDependencies[0] == ':library'
        foundDependencies[1] == ':library2'
        foundDependencies[2] == null
    }

    def 'visit adding file collections and other configurations into a configuration'() {
        given:
        project.configurations.create('customConfig')
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(JavaGradlePluginPlugin)
        project.buildFile << """
            def aVariable = fileTree('dir2') {
                include '*.jar'
            }

            dependencies {
                customConfig gradleApi()
                customConfig sourceSets.main.output
                customConfig fileTree('dir')
                customConfig configurations.compile
                customConfig aVariable
            }
        """

        when:
        def rule = new DependencyVisitingRule().run()

        then:
        rule.allGradleDependencies.size() == 1
        rule.objectDependencies.size() == 4
    }

    def 'visit dependencies in a project path project block'() {
        when:
        def subproject = addSubproject('test')
        subproject.configurations.create('customConfig')
        project.subprojects.add(subproject)
        project.buildFile << """
            project(':test') {
                dependencies {
                   customConfig 'b:b:1'
                }
            }
        """

        def b = new DependencyVisitingRule().run().allGradleDependencies.find { it.name == 'b' }

        then:
        b
        b.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'visit dependencies in a dynamic project path project block'() {
        when:
        def subproject = addSubproject('test')
        subproject.configurations.create('customConfig')
        project.subprojects.add(subproject)
        project.buildFile << """
            def projectName = ':test'
            project("\$projectName") {
                dependencies {
                   customConfig 'b:b:1'
                }
            }
        """

        def b = new DependencyVisitingRule().run().allGradleDependencies.find { it.name == 'b' }

        then:
        b
        b.syntax == GradleDependency.Syntax.StringNotation
    }

    def 'visit dependencies is not broken when project attribute is accessed'() {
        when:
        project.buildFile << """
            dependencies.ext.myTestDependencies = { String confName = 'compile' ->
                project.apply(plugin: 'java')
                project.dependencies {
                   compile 'a:a:1.0'
                }
            }
            dependencies {
                myTestDependencies()
            }
        """

        new DependencyVisitingRule().run()

        then:
        noExceptionThrown()
    }

    def 'visit dependencies that are defined with map notation'() {
        when:
        project.buildFile << """
            dependencies {
               compile group: 'a', name: 'a', version: '1'
            }
        """

        def a = new DependencyVisitingRule().run().deps.find { it.name == 'a' }

        then:
        a
        a.group == 'a'
        a.version == '1'
        a.syntax == GradleDependency.Syntax.MapNotation
    }

    def 'visit dependencies that are defined with GString and braces are preserved'() {
        given:
        project.buildFile << """
            def v = 1
            dependencies {
                compile group: 'a', name: 'a', version: "\${v}"
                compile "b:b:\${v}"
            }
        """

        when:
        def a = new DependencyVisitingRule().run().deps.find { it.name == 'a' }
        def b = new DependencyVisitingRule().run().deps.find { it.name == 'b' }

        then:
        a.version == '${v}'
        b.version == '${v}'
    }

    def 'visit dependency with no version'() {
        when:
        project.buildFile << """
            dependencies {
               compile 'a:a'
            }
        """

        def a = new DependencyVisitingRule().run().deps.find { it.name == 'a' }

        then:
        a
        a.version == null
    }

    def 'add violation with deletion'() {
        when:
        project.buildFile << "apply plugin: 'java'"

        def rule = new GradleLintRule() {
            String description = 'test'

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
            String description = 'test'

            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                bookmark('lastApplyPlugin', call)
            }

            @Override
            void visitDependencies(MethodCallExpression call) {
                if (bookmark('lastApplyPlugin')) {
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
        def noPluginsRule = setupNoPluginsRule()

        when:
        project.buildFile << """
            gradleLint.ignore($rules) { apply plugin: 'java' }
        """

        def result = runRulesAgainst(noPluginsRule)

        then:
        result.violates() == violates

        where:
        rules                               | violates
        /'no-plugins-allowed'/              | false
        /'other-rule'/                      | true
        /'no-plugins-allowed','other-rule'/ | false
        ''                                  | false
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

    @Unroll
    def "fixme is treated like an ignore if its predicate is a future date #inTheFuture"() {
        setup:
        def noPluginsRule = setupNoPluginsRule()

        when:
        project.buildFile << """
            gradleLint.fixme('$inTheFuture' ${rules.isEmpty() ? '' : ','} $rules) { apply plugin: 'java' }
        """

        def result = runRulesAgainst(noPluginsRule)

        then:
        result.violates() == violates

        where:
        rules                               | violates
        /'no-plugins-allowed'/              | false
        /'other-rule'/                      | true
        /'no-plugins-allowed','other-rule'/ | false
        ''                                  | false

        inTheFuture = DateTime.now().plusMonths(1).toString(DateTimeFormat.forPattern('MM/d/yyyy'))
    }

    @Unroll
    def 'fixme fails the build if its predicate is a date in the past or is unparseable (#oldDate)'() {
        setup:
        def noPluginsRule = setupNoPluginsRule()

        when:
        project.buildFile << """
            gradleLint.fixme('$oldDate') { apply plugin: 'java' }
        """

        def results = runRulesAgainst(noPluginsRule)

        then:
        results.violates()

        where:
        oldDate << ['unparseable', '2010-12-1', '12/1/2010', '12/1/10']
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
            String description = 'test'

            @Override
            void visitExtensionProperty(ExpressionStatement expression, String extension, String prop) {
                if (extension == 'nebula' && prop == 'moduleOwner')
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
            String description = 'test'

            @Override
            void visitExpressionStatement(ExpressionStatement statement) {
                if (statement.expression instanceof VariableExpression)
                    parent = parentNode()
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
            String description = 'test'

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                if (call.methodAsString == 'multiline')
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

    def 'insertIntoClosure indents 4 spaces'() {
        when:
        project.buildFile << """\
            block {
                something 'else'
            }
        """.stripIndent()

        String corrected = correct(new GradleLintRule() {
            String description = 'test insertIntoClosure'

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                if (call.methodAsString == 'block')
                    addBuildLintViolation('inserting into block', call)
                .insertIntoClosure(call, 'inserted true')
            }
        })

        then:
        String expected = """\
            block {
                inserted true
                something 'else'
            }
        """.stripIndent()
        corrected.equals(expected)
    }

    def 'insertIntoClosure indents 4 spaces - nested'() {
        when:
        project.buildFile << """\
            wrapped {
                block {
                    something 'else'
                }
            }
        """.stripIndent()

        String corrected = correct(new GradleLintRule() {
            String description = 'test insertIntoClosure'

            @Override
            void visitMethodCallExpression(MethodCallExpression call) {
                if (call.methodAsString == 'block')
                    addBuildLintViolation('inserting into block', call)
                            .insertIntoClosure(call, 'inserted true')
            }
        })

        then:
        String expected = """\
            wrapped {
                block {
                    inserted true
                    something 'else'
                }
            }
        """.stripIndent()
        corrected.equals(expected)
    }

    def 'visit resolution strategy forces'() {
        when:
        project.buildFile << """
            apply plugin: 'java'
            
            configurations {
                all {
                    resolutionStrategy {
                        force 'com.google.guava:guava:19.0'
                    }
                }
            }
        """

        def foundForces = []
        def rule = new GradleLintRule() {
            String description = 'test'

            @Override
            void visitGradleResolutionStrategyForce(MethodCallExpression call, String conf, Map<GradleDependency, Expression> forces) {
                foundForces = forces.values()
            }
        }

        runRulesAgainst(rule)

        then:
        !foundForces.isEmpty()
    }

    /**
     * @returns A simple rule barring the use of any `apply plugin` statements that
     * we can use in this test harness.
     */
    GradleLintRule setupNoPluginsRule() {
        def noPluginsRule = new GradleLintRule() {
            String description = 'test'

            @Override
            void visitApplyPlugin(MethodCallExpression call, String plugin) {
                addBuildLintViolation('no plugins allowed', call)
            }
        }
        noPluginsRule.ruleId = 'no-plugins-allowed'

        new File(temp.root, 'META-INF/lint-rules').mkdirs()
        def noPluginsProp = temp.newFile("META-INF/lint-rules/no-plugins-allowed.properties")
        noPluginsProp << "implementation-class=${noPluginsRule.class.name}"
        LintRuleRegistry.classLoader = new URLClassLoader([temp.root.toURI().toURL()] as URL[], getClass().getClassLoader())

        noPluginsRule
    }

    abstract class GradleProjectLintRule extends GradleLintRule implements GradleModelAware {}

    private class DependencyVisitingRule extends GradleLintRule implements GradleModelAware {
        final String description = 'visit dependencies'
        List<GradleDependency> deps = []
        List<GradleDependency> allprojectDeps = []
        List<GradleDependency> subprojectDeps = []
        List<GradleDependency> allGradleDependencies = []
        List<GradleDependency> buildscriptDeps = []
        List<String> submoduleDependencies = []
        List<Object> objectDependencies = []

        @Override
        void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
            deps += dep
        }

        @Override
        void visitBuildScriptDependency(MethodCallExpression call, String conf, GradleDependency dep) {
            buildscriptDeps += dep
        }

        @Override
        void visitSubprojectGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
            subprojectDeps += dep
        }

        @Override
        void visitAllprojectsGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
            allprojectDeps += dep
        }

        @Override
        void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
            allGradleDependencies += dep
        }

        @Override
        void visitAnySubmoduleDependency(MethodCallExpression call, String conf, String dep) {
            submoduleDependencies += dep
        }

        @Override
        void visitAnyObjectDependency(MethodCallExpression call, String conf, Object dep) {
            objectDependencies.add(dep)
        }

        DependencyVisitingRule run() { runRulesAgainst(this); this }
    }
}
