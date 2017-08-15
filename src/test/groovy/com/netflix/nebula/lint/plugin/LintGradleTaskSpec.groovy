/*
 * Copyright 2016-2017 Netflix, Inc.
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

class LintGradleTaskSpec extends TestKitSpecification {
    def 'mark violations that have no auto-remediation'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                compile 'com.google.guava:guava:18.0'
                compile 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        createJavaSourceFile('public class Main {}')
        def result = runTasksSuccessfully('compileJava', 'lintGradle')

        then:
        result.output.contains('(no auto-fix available)')
    }

    def 'critical rule failures cause build failure'() {
        when:
        debug = true
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.criticalRules = ['dependency-parentheses']

            repositories { mavenCentral() }

            dependencies {
                compile('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasksFail('lintGradle')
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
                    compile('com.google.guava:guava:18.0')
                }
            }
        """

        then:
        runTasksFail('lintGradle')
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
        def result = runTasksSuccessfully('lintGradle')

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
                compile 'com.google.guava:guava:18.0'
                compile 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        createJavaSourceFile('public class Main { uhoh! }')
        def result = runTasksFail('compileJava')

        then:
        result.task(':autoLintGradle').outcome == TaskOutcome.SUCCESS
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
                compile 'com.google.guava:guava:18.0'
                compile 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        createJavaSourceFile('public class Main { uhoh! }')
        def result = runTasksFail('compileJava')

        then:
        result.task(':autoLintGradle').outcome == TaskOutcome.SKIPPED
    }
}
