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
import com.netflix.nebula.lint.GradleVersions
import com.netflix.nebula.lint.rule.dependency.DependencyParenthesesRule
import com.netflix.nebula.lint.rule.dependency.UnusedDependencyRule
import spock.lang.Issue
import spock.lang.Subject
import spock.lang.Unroll

@Subject([UnusedDependencyRule, DependencyParenthesesRule])
class FixGradleLintTaskSpec extends BaseIntegrationTestKitSpec {
    def 'overlapping patches result in unfixed or semi-fixed results'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.criticalRules = ['unused-dependency', 'dependency-parentheses']

            repositories { mavenCentral() }

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        writeHelloWorld()

        then:
        def results = runTasksAndFail('fixGradleLint', '--warning-mode', 'all')

        results.output.count('fixed          unused-dependency') == 1
        results.output.count('unfixed        dependency-parentheses') == 1
    }

    @Unroll
    def 'lint fixes violation in all applied files'() {
        setup:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            
            repositories {
                mavenCentral()
            }

            gradleLint.rules = ['dependency-parentheses']
            
            dependencies {
                implementation('commons-lang:commons-lang:2.6')
            }

            apply from: 'dependencies.gradle'
        """
        File dependenciesFile = new File(projectDir, 'dependencies.gradle')
        dependenciesFile.text = """
            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        writeHelloWorld()

        when:
        gradleVersion = testGradleVersion
        def results = runTasks('fixGradleLint')

        then:
        results.output.count('fixed          dependency-parentheses') == 2
        dependenciesFile.text.contains('implementation \'com.google.guava:guava:18.0\'')
        buildFile.text.contains('implementation \'commons-lang:commons-lang:2.6\'')

        where:
        testGradleVersion << GradleVersions.ALL
    }

    @Issue('#37')
    def 'patches involving carriage returns apply'() {
        when:
        buildFile.text = """\
plugins {\r
    id 'nebula.lint'\r
    id 'java'\r
}\r
\r
gradleLint.rules = ['dependency-parentheses']\r
\r
repositories { mavenCentral() }\r
\r
dependencies {\r
    implementation('com.google.guava:guava:18.0')\r
}"""

        then:
        runTasks('fixGradleLint')
        buildFile.text.contains("implementation 'com.google.guava:guava:18.0")
    }
}
