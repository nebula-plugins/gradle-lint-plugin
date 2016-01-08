package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.rule.dependency.UnusedDependencyExcludeRule
import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

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
}
