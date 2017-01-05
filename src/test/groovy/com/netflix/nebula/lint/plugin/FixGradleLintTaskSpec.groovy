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

    def 'Make sure logging works on older gradle version'() {
        buildFile << """\
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['all-dependencies']
            """.stripIndent()

        when:
        def results = runTasksSuccessfullyWithGradleVersion('2.13', 'assemble', 'lintGradle')

        then:
        println results?.output
        noExceptionThrown()
    }
}
