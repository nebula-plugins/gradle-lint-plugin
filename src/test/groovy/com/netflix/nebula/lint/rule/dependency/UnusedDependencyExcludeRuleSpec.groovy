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

package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec
import spock.lang.Issue

class UnusedDependencyExcludeRuleSpec extends AbstractRuleSpec {
    def rule

    def setup() {
        rule = new UnusedDependencyExcludeRule(project: project)
    }

    def 'unused exclude violates'() {
        when:
        // trivial case: no dependencies
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()
    }

    def 'exclude matching a transitive dependency does not violate'() {
        when:
        // trivial case: no dependencies
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'commons-lang', module: 'commons-lang'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'commons-lang', module: 'commons-lang'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.doesNotViolate()
    }

    @Issue('#57')
    def 'exclude on a dependency that is unresolvable is considered unapplicable'() {
        when:
        // trivial case: no dependencies
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                compile('dne:dne:1.0') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                compile('dne:dne:1.0') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()
    }
}
