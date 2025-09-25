/*
 * Copyright 2015-2025 Netflix, Inc.
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
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
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
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()
    }

    def 'unused exclude violates - api configuration'() {
        when:
        project.buildFile << """
            apply plugin: 'java-library'
            dependencies {
                api('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        """

        project.with {
            apply plugin: 'java-library'
            repositories {
                mavenCentral()
            }
            dependencies {
                api('commons-configuration:commons-configuration:1.10') {
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

        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
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
                implementation('commons-configuration:commons-configuration:1.10') {
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
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('dne:dne:1.0') {
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
                implementation('dne:dne:1.0') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()
    }

    def 'detects multiple unused excludes on same dependency'() {
        when:
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                    exclude group: 'com.fasterxml.jackson.core', module: 'jackson-core'
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
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                    exclude group: 'com.fasterxml.jackson.core', module: 'jackson-core'
                    exclude group: 'commons-lang', module: 'commons-lang'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()

        results.violations.size() == 2
        results.violations.any { it.message.contains('the excluded dependency is not a transitive') && it.sourceLine.contains('com.google.guava') }
        results.violations.any { it.message.contains('the excluded dependency is not a transitive') && it.sourceLine.contains('com.fasterxml.jackson.core') }
        !results.violations.any { it.sourceLine.contains('commons-lang') && it.sourceLine.contains('commons-lang') }
    }

    def 'works with testImplementation configuration'() {
        when:
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                testImplementation('junit:junit:4.13.2') {
                    exclude group: 'fake.group', module: 'fake-module'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                testImplementation('junit:junit:4.13.2') {
                    exclude group: 'fake.group', module: 'fake-module'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()
        results.violations[0].message.contains('the excluded dependency is not a transitive')
        results.violations[0].sourceLine.contains('fake.group') && results.violations[0].sourceLine.contains('fake-module')
    }

    def 'handles exclude with only group specified'() {
        when:
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()
        results.violations[0].message.contains('the excluded dependency is not a transitive')
    }

    def 'handles exclude with only module specified'() {
        when:
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude module: 'guava'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude module: 'guava'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()
        results.violations[0].message.contains('the excluded dependency is not a transitive')
    }

    @Issue('Gradle 9.x compatibility - detached configurations')
    def 'rule creates and cleans up configurations properly'() {
        when:
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('commons-configuration:commons-configuration:1.10') {
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
                implementation('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        }

        def configurationsBefore = project.configurations.collect { it.name }
        def results = runRulesAgainst(rule)
        def configurationsAfter = project.configurations.collect { it.name }

        then:
        results.violates()
        configurationsBefore == configurationsAfter
        !configurationsAfter.any { it.contains('lintExcludes') }
    }

    def 'handles complex dependency with multiple transitives'() {
        when:
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation('org.springframework:spring-web:5.3.0') {
                    exclude group: 'org.springframework', module: 'spring-core' // valid
                    exclude group: 'fake.spring', module: 'fake-spring-core' // invalid
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation('org.springframework:spring-web:5.3.0') {
                    exclude group: 'org.springframework', module: 'spring-core'
                    exclude group: 'fake.spring', module: 'fake-spring-core'
                }
            }
        }

        def results = runRulesAgainst(rule)

        then:
        results.violates()

        results.violations.size() == 1
        results.violations[0].message.contains('the excluded dependency is not a transitive')
        results.violations[0].sourceLine.contains('fake.spring') && results.violations[0].sourceLine.contains('fake-spring-core')
        !results.violations.any { it.sourceLine.contains('spring-core') && !it.sourceLine.contains('fake') }
    }
}
