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

    def 'first order dependency versions not meeting the minimum are upgraded'() {
        when:
        buildFile << """
            dependencies {
                compile 'com.google.guava:guava:18.+'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully(*tasks)
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0']
    }

    def 'transitive dependencies not meeting the minimum result in a first-order dependency being added'() {
        when:
        buildFile << """
            dependencies {
                // depends on guava 18.0
                compile 'io.grpc:grpc-core:0.13.2'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def results = runTasksSuccessfully(*tasks)
        println(results.output)
        println(buildFile.text)
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0', 'io.grpc:grpc-core:0.13.2']
    }

    /**
     * FIXME how to implement this?
     */
    @Ignore
    def 'resolution strategies preventing us from reaching the minimum version are changed'() {
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
        runTasksSuccessfully(*tasks)
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0']
    }

    def "interpolated values are changed if necessary"() {
        when:
        buildFile << """
            ext.GUAVA_VERSION = '18.0'

            dependencies {
                compile "com.google.guava:guava:\$GUAVA_VERSION"
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully(*tasks)
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0']
    }

    def "forces preventing us from reaching the minimum version are updated"() {
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

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully(*tasks)
        buildFile.text.contains("force 'com.google.guava:guava:19.0'")
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
                compile 'com.google.guava:guava'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        runTasksSuccessfully(*tasks)
        dependencies(buildFile, 'compile') == ['com.google.guava:guava']
    }

    def 'ignore configurations from java library plugin'() {
        // TODO: Sometime, refactor the project to support java library adjustments

        given:
        definePluginOutsideOfPluginBlock = true

        buildFile.delete()

        buildFile << """
            apply plugin: 'nebula.lint'
            apply plugin: 'nebula.configEnvironment'
            apply plugin: 'java-library'
            gradleLint.rules = ['minimum-dependency-version']
            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:18.+'
            }
            """.stripIndent()

        createJavaSourceFile('public class Main {}')

        when:
        runTasksSuccessfully(*tasks)

        then:
        dependencies(buildFile, 'implementation') == ['com.google.guava:guava:18.+']
    }
}
