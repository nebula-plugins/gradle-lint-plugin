/**
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule.dependency

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import spock.lang.Subject
import spock.lang.Unroll

@Subject(BypassedForcesRule)
class BypassedForcesWithResolutionRulesSpec extends IntegrationTestKitSpec {
    File rulesJsonFile

    def setup() {
        setupProjectAndDependencies()
        debug = true
        forwardOutput = true
    }

    @Unroll
    def 'direct dependency force is honored - force to good version while substitution is triggered by a transitive dependency | core alignment #coreAlignment'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.1.0') {
                    force = true
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // force to an okay version is the primary contributor; the substitution rule was a secondary contributor
        results.output.contains 'test.nebula:a:1.2.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.1.0\n'

        results.output.contains 'aligned'
        results.output.contains '- Forced'

        results.output.contains('0 violations')

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'direct dependency force not honored - force to bad version triggers a substitution | core alignment #coreAlignment'() {
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.2.0') {
                    force = true // force to bad version triggers a substitution
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assert results.output.contains('test.nebula:a:1.2.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:b:1.0.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:c:1.0.0 -> 1.3.0\n')

        assert results.output.contains('This project contains lint violations.')
        assert results.output.contains('bypassed-forces')
        assert results.output.contains('The force specified for dependency \'test.nebula:a\' has been bypassed')

        results.output.contains 'aligned'
        results.output.contains('- Forced')

        where:
        coreAlignment << [true]
    }

    void setupProjectAndDependencies() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:a:1.2.0')
                .addModule('test.nebula:a:1.3.0')

                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.nebula:b:1.2.0')
                .addModule('test.nebula:b:1.3.0')

                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:1.1.0')
                .addModule('test.nebula:c:1.2.0')
                .addModule('test.nebula:c:1.3.0')

                .addModule(new ModuleBuilder('test.other:z:1.0.0').addDependency('test.nebula:a:1.2.0').build())

                .build()
        File mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        buildFile << """\
            plugins {
                id 'java'
                id "nebula.resolution-rules" version "7.5.0" 
                id 'nebula.lint'
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            gradleLint.rules = ['bypassed-forces']
            """.stripIndent()

        String reason = "★ custom substitution reason"
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module": "test.nebula:a:1.2.0",
                        "with": "test.nebula:a:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "module": "test.nebula:b:1.2.0",
                        "with": "test.nebula:b:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "module": "test.nebula:c:1.2.0",
                        "with": "test.nebula:c:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ],
                "align": [
                    {
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()
    }

}
