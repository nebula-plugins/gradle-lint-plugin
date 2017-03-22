/*
 * Copyright 2015-2017 Netflix, Inc.
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
package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.TestKitSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Unroll

class GradleLintPluginSpec extends TestKitSpecification {
    def 'run multiple rules on a single module project'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasksSuccessfully('assemble')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
    }

    def 'run rules on multi-module project where one of the subprojects has no build.gradle'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
            }

            subprojects {
                apply plugin: 'nebula.lint'
                apply plugin: 'java'
            }

            allprojects {
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }
        """

        addSubproject('sub')

        then:
        runTasksSuccessfully('lintGradle')
    }

    def 'run rules on multi-module project where one of the subprojects does not apply gradle lint'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
            }

            subprojects {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
        """

        addSubproject('sub', """
            dependencies {
                compile('a:a:1')
            }
        """)

        then:
        runTasksSuccessfully('lintGradle')
    }

    @Unroll
    def 'auto correct all violations on a single module project with task #taskName'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasksSuccessfully(taskName)

        buildFile.text == """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """.toString()

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    @Unroll
    def 'auto correct all violations on a multi-module project with task #taskName'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    compile('com.google.guava:guava:18.0')
                }
            }
        """

        def subDir = addSubproject('sub', """
            dependencies {
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        then:
        runTasksSuccessfully(taskName)

        buildFile.text.contains("compile 'com.google.guava:guava:18.0'")
        new File(subDir, 'build.gradle').text.contains("testCompile 'junit:junit:4.11'")

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    @Unroll
    def 'auto correct violations on a multi-module project where one of the subprojects does not apply gradle lint with task #taskName'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
            }
            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
        """

        addSubproject('sub', """
            apply plugin: 'java'
            apply plugin: 'nebula.lint'
            
            dependencies {
                compile('a:a:1')
            }
        """)

        then:
        runTasksSuccessfully(taskName)

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    def 'rules relative to each project'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                
                dependencies {
                    compile('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        then:
        def results = runTasksSuccessfully('taskA')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
    }

    def 'generate a lint report for a single module project'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasksSuccessfully('generateGradleLintReport')
        println results.output

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
        new File(projectDir, "build/reports/gradleLint/${projectDir.name}.html").exists()
    }

    def 'test wrapper rule on a single module project'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['archaic-wrapper']

            task wrapper(type: Wrapper){
                gradleVersion = '0.1'
            }
        """

        then:
        def results = runTasksSuccessfully('lintGradle')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 1
        console.any { it.contains('archaic-wrapper') }
    }

    @Issue('#68')
    def 'lint task does not run when alwaysRun is off'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint {
                rules = ['archaic-wrapper']
                alwaysRun = false
            }

            task wrapper(type: Wrapper){
                gradleVersion = '0.1'
            }
        """

        then:
        // build would normally trigger lintGradle, but will not when alwaysRun = false
        def results = runTasksSuccessfully('build')

        when:
        def console = results.output.readLines()

        then:
        !console.any { it.contains('archaic-wrapper') }
    }

    def 'autoLintGradle is always run'() {
        createJavaSourceFile('public class Main { }')
        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            gradleLint.rules = ['dependency-parentheses']
            """.stripIndent()

        when:
        def results = runTasksSuccessfully('compileJava')

        then:
        results.task(':autoLintGradle').outcome == TaskOutcome.SUCCESS
    }

    def 'override rule set with a gradle property'() {
        createJavaSourceFile('public class Main { }')
        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile('com.google.guava:guava:21.0')
            }
            """.stripIndent()

        when:
        def results = runTasksSuccessfully('fixGradleLint',
                '-PgradleLint.rules=dependency-parentheses')

        then:
        results.task(':fixGradleLint').outcome == TaskOutcome.SUCCESS
        buildFile.text.contains("compile 'com.google.guava:guava:21.0'")
    }

    def 'exclude individual rules with a gradle property or gradleLint.excludedRules'() {
        when:
        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            gradleLint.criticalRules = ['all-dependency', 'dependency-parentheses']
            
            // realistically, we wouldn't exclude something we just directly included...
            gradleLint.excludedRules = ['dependency-parentheses']
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile('com.google.guava:guava:21.0')
            }
            """.stripIndent()

        createJavaSourceFile('public class Main { }')

        then:
        runTasksSuccessfully('lintGradle', '-PgradleLint.excludedRules=unused-dependency')
    }

    def 'lint Gradle sources included with `apply from`'() {
        when:
        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            gradleLint.rules = ['dependency-parentheses']
            
            apply from: 'dependencies.gradle'
            """.stripIndent()

        def applyFrom = new File(projectDir, 'dependencies.gradle')
        applyFrom.text = """\
            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile('com.google.guava:guava:21.0')
            }
        """

        createJavaSourceFile('public class Main { }')

        then:
        runTasksSuccessfully('fixGradleLint')
        applyFrom.text.contains("compile 'com.google.guava:guava:21.0'")
    }

    @Issue('#100')
    def 'empty build file lints successfully'() {
        debug = true

        when:
        buildFile << """\
            plugins {
                id 'nebula.lint'
            }

            subprojects {
                apply plugin: 'nebula.lint'
                apply plugin: 'java'

                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }
            """.stripIndent()

        new File(addSubproject('sub1'), 'build.gradle').createNewFile()

        then:
        runTasksSuccessfully('fixGradleLint')
    }
}
