package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Issue
import spock.lang.Unroll

class OverriddenDependencyVersionRuleSpec extends TestKitSpecification {
    def setup() {
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['overridden-dependency-version']

            repositories { mavenCentral() }
        """
    }

    def 'fixed versions affected by forces are changed'() {
        when:
        buildFile << """
            configurations.compile {
                resolutionStrategy {
                    force 'com.google.guava:guava:19.0'
                }
            }

            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully('assemble', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0']
    }

    def 'fixed versions affected by resolution strategies are changed'() {
        when:
        buildFile << """
            configurations.compile {
                resolutionStrategy {
                    eachDependency { details ->
                        if (details.requested.name == 'guava') {
                            details.useTarget group: details.requested.group, name: details.requested.name, version: '19.0'
                        }
                    }
                }
            }

            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully('assemble', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0']
    }

    def "interpolated values are left unchanged"() {
        when:
        buildFile << """
            ext.GUAVA_VERSION = '18.0'

            dependencies {
                compile "com.google.guava:guava:\$GUAVA_VERSION"
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully('assemble', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:$GUAVA_VERSION']
    }

    @Unroll
    def "dynamic versions like '#version' that can't match the resolved version are changed"() {
        when:
        buildFile << """
            configurations.compile {
                resolutionStrategy {
                    force 'com.google.guava:guava:18.0'
                }
            }

            dependencies {
                compile 'com.google.guava:guava:$version'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully('assemble', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:18.0']

        where:
        version << ['19.+', '[16.0, 17.0]']
    }

    def "latest constraints that COULD match a resolved version do, even if the resolved version isn't actually the latest"() {
        when:
        buildFile << """
            configurations.compile {
                resolutionStrategy {
                    force 'com.google.guava:guava:18.0'
                }
            }

            dependencies {
                compile 'com.google.guava:guava:latest.release' // there is a version 19.0 out
            }
        """

        then:
        runTasksSuccessfully('assemble', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:latest.release']
    }

    @Issue('55')
    def 'leave dependencies without a version alone'() {
        when:
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    eachDependency { DependencyResolveDetails details ->
                        details.useVersion '19.0'
                    }
                }
            }

            dependencies {
                compile 'com.google.guava:guava'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully('assemble', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.guava:guava']
    }
}
