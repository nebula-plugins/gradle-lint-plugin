package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Ignore

class MinimumDependencyVersionRuleSpec extends TestKitSpecification {
    def tasks = ['assemble', 'fixGradleLint', '-PgradleLint.minVersions=com.google.guava:guava:19.0']

    def setup() {
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'nebula.configEnvironment'
                id 'java'
            }

            gradleLint.rules = ['minimum-dependency-version']

            repositories { mavenCentral() }
        """.stripIndent()
    }

    def 'warn first order dependency versions not meeting the minimum - api configuration'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'nebula.configEnvironment'
                id 'java-library'
            }

            gradleLint.rules = ['minimum-dependency-version']

            repositories { mavenCentral() }
            
            dependencies {
                api 'com.google.guava:guava:18.+'
            }
        """.stripIndent()

        createJavaSourceFile('public class Main {}')

        then:
        def result = runTasksSuccessfully(*tasks)
        result.output.contains('needs fixing   minimum-dependency-version         com.google.guava:guava is below the minimum version of 19.0')
    }


    def 'warn first order dependency versions not meeting the minimum'() {
        when:
        buildFile << """
            dependencies {
                implementation 'com.google.guava:guava:18.+'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def result = runTasksSuccessfully(*tasks)
        result.output.contains('needs fixing   minimum-dependency-version         com.google.guava:guava is below the minimum version of 19.0')
    }

    def 'warn transitive dependencies not meeting the minimum result in a first-order dependency'() {
        when:
        buildFile << """
            dependencies {
                // depends on guava 18.0
                implementation 'io.grpc:grpc-core:0.13.2'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def results = runTasksSuccessfully(*tasks)
        results.output.contains('needs fixing   minimum-dependency-version         com.google.guava:guava is below the minimum version of 19.0')
    }

    /**
     * FIXME how to implement this?
     */
    @Ignore
    def 'warn resolution strategies preventing us from reaching the minimum version are changed'() {
        when:
        buildFile << """
            configurations.implementation {
                resolutionStrategy {
                    eachDependency { details ->
                        if (details.requested.name == 'guava') {
                            details.useTarget group: details.requested.group, name: details.requested.name, version: '19.0'
                        }
                    }
                }
            }

            dependencies {
                implementation 'com.google.guava:guava:18.0'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully(*tasks)
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0']
    }

    def "warn interpolated values should be changed if necessary"() {
        when:
        buildFile << """
            ext.GUAVA_VERSION = '18.0'

            dependencies {
                implementation "com.google.guava:guava:\$GUAVA_VERSION"
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def result = runTasksSuccessfully(*tasks)
        result.output.contains('needs fixing   minimum-dependency-version         com.google.guava:guava is below the minimum version of 19.0')
    }

    def "warn when forces preventing us from reaching the minimum version" () {
        when:
        buildFile << """
            configurations.compileClasspath {
                resolutionStrategy {
                    force 'com.google.guava:guava:18.0'
                }
            }

            dependencies {
                implementation 'com.google.guava:guava:latest.release' // there is a version 19.0 out
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def result = runTasksSuccessfully(*tasks)
        result.output.contains("needs fixing   minimum-dependency-version         this dependency does not meet the minimum version of 19.0")
    }

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
                implementation 'com.google.guava:guava'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully(*tasks)
        dependencies(buildFile, 'implementation') == ['com.google.guava:guava']
    }
}
