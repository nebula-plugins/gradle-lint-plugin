/*
 * Copyright 2016-2019 Netflix, Inc.
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

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import com.netflix.nebula.lint.rule.dependency.DependencyParenthesesRule
import com.netflix.nebula.lint.rule.dependency.FirstOrderDuplicateDependencyClassRule
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Subject
import spock.lang.Unroll

@Subject([FirstOrderDuplicateDependencyClassRule, DependencyParenthesesRule])
class LintGradleTaskSpec extends BaseIntegrationTestKitSpec {

    @Unroll
    def 'mark violations that have no auto-remediation - configuration #configuration'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                $configuration 'com.google.guava:guava:18.0'
                $configuration 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        writeHelloWorld()

        if (configuration == 'compile') {
            System.setProperty("ignoreDeprecations", "true")
        }
        def result = runTasks('compileJava')
        if (configuration == 'compile') {
            System.setProperty("ignoreDeprecations", "false")
        }

        then:
        result.output.contains('(no auto-fix available)')

        where:
        configuration << ['implementation']
    }

    def 'critical rule failures cause build failure'() {
        when:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.criticalRules = ['dependency-parentheses']

            repositories { mavenCentral() }

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasksAndFail('lintGradle')
    }

    def 'expired fixmes cause build failure'() {
        when:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            gradleLint.rules = ['dependency-parentheses']

            repositories { mavenCentral() }

            dependencies {
                gradleLint.fixme('2010-1-1') {
                    implementation('com.google.guava:guava:18.0')
                }
            }
        """

        then:
        runTasksAndFail('lintGradle')
    }

    def 'lintGradle always depends on compileJava'() {
        given:
        buildFile.text = """\
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            gradleLint.rules = ['dependency-parentheses']
            """.stripIndent()

        when:
        def result = runTasks('lintGradle')

        then:
        result.task(':compileJava').outcome == TaskOutcome.NO_SOURCE
    }

    def 'auto lint runs on build failure'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                implementation 'com.google.guava:guava:18.0'
                implementation 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        writeJavaSourceFile('public class Main { uhoh! }')
        def result = runTasksAndFail('compileJava')

        then:
        result.output.contains('This project contains lint violations.')
    }


    def 'auto lint does not run on build failure when autoLintAfterFailure is false'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint {
                rules = ['duplicate-dependency-class']
                autoLintAfterFailure = false
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'com.google.guava:guava:18.0'
                implementation 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        writeJavaSourceFile('public class Main { uhoh! }')
        def result = runTasksAndFail('compileJava')

        then:
        !result.output.contains('This project contains lint violations.')
    }

    def 'lint finds all violations in all applied files with bookmark rule'() {
        given:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            
            repositories {
                mavenCentral()
            }
            
            def someVariable = 1

            gradleLint.rules = ['dependency-parentheses']
            
            dependencies {
                implementation('commons-lang:commons-lang:2.5')
            }

            allprojects {
                apply from: 'dependencies.gradle'
            }
        """
        File dependenciesFile = new File(projectDir, 'dependencies.gradle')
        dependenciesFile.text = """

            def someVariable = 2

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
            
            apply from: 'another.gradle'
        """

        File another = new File(projectDir, 'another.gradle')
        another.text = """
            dependencies {
                implementation('com.google.guava:guava:17.0')
            }
        """

        when:
        def results = runTasksAndFail('lintGradle')

        then:
        results.output.count('warning   dependency-parentheses             parentheses are unnecessary for dependencies') == 3
        results.output.contains('another.gradle:3')
        results.output.contains('dependencies.gradle:6')
        results.output.contains('build.gradle:16')
    }
}
