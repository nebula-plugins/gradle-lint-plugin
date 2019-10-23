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

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Issue

class FixGradleLintTaskSpec extends TestKitSpecification {

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
                compile('com.google.guava:guava:18.0')
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def results = runTasksFail('fixGradleLint')

        results.output.count('fixed          unused-dependency') == 1
        results.output.count('unfixed        dependency-parentheses') == 1
    }

    def 'lint fixes violation in all applied files'() {
        when:
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
                compile('commons-lang:commons-lang:2.6')
            }

            apply from: 'dependencies.gradle'
        """
        File dependenciesFile = new File(projectDir, 'dependencies.gradle')
        dependenciesFile.text = """
            dependencies {
                compile('com.google.guava:guava:18.0')
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def results = runTasksSuccessfully('fixGradleLint')
        results.output.count('fixed          dependency-parentheses') == 2
        dependenciesFile.text.contains('compile \'com.google.guava:guava:18.0\'')
        buildFile.text.contains('compile \'commons-lang:commons-lang:2.6\'')
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
    compile('com.google.guava:guava:18.0')\r
}"""

        then:
        runTasksSuccessfully('fixGradleLint')
        buildFile.text.contains("compile 'com.google.guava:guava:18.0")
    }

    /**
     * Because Gradle changed the internal APIs we are using to performed stylized text logging...
     * This verifies that our reflection hack continues to be backwards compatible
     */
    def 'Make sure logging works on older gradle version'() {
        buildFile << """\
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['all-dependencies']
            """.stripIndent()
        gradleVersion = '4.2' //we don't support older versions anymore

        when:
        def results = runTasksSuccessfully('assemble', 'lintGradle')

        then:
        println results?.output
        noExceptionThrown()
    }
}
