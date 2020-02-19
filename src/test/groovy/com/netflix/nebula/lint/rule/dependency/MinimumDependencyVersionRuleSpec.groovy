package com.netflix.nebula.lint.rule.dependency

import nebula.test.IntegrationTestKitSpec
import spock.lang.Ignore
import spock.lang.Subject

@Subject(MinimumDependencyVersionRule)
class MinimumDependencyVersionRuleSpec extends IntegrationTestKitSpec {
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

        debug = true
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

        writeHelloWorld()

        then:
        def result = runTasks(*tasks)
        result.output.contains('needs fixing   minimum-dependency-version         com.google.guava:guava is below the minimum version of 19.0')
    }


    def 'warn first order dependency versions not meeting the minimum'() {
        when:
        buildFile << """
            dependencies {
                implementation 'com.google.guava:guava:18.+'
            }
        """

        writeHelloWorld()

        then:
        def result = runTasks(*tasks)
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

        writeHelloWorld()

        then:
        def results = runTasks(*tasks)
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

        writeHelloWorld()

        then:
        runTasks(*tasks)
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

        writeHelloWorld()

        then:
        def result = runTasks(*tasks)
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

        writeHelloWorld()

        then:
        def result = runTasks(*tasks)
        result.output.contains("needs fixing   minimum-dependency-version         com.google.guava:guava does not meet the minimum version of 19.0")
    }

    def 'warn when resolution strategy preventing us from reaching the minimum version'() {
        when:
        buildFile << """
            configurations.all {
                resolutionStrategy {
                    eachDependency { DependencyResolveDetails details ->
                        details.useVersion '18.0'
                    }
                }
            }

            dependencies {
                implementation 'com.google.guava:guava'
            }
        """

        writeHelloWorld()

        then:
        def result = runTasks(*tasks)
        result.output.contains("needs fixing   minimum-dependency-version         com.google.guava:guava is below the minimum version of 19.0")
    }

    def 'leave dependencies using resolution strategy with correct version alone'() {
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

        writeHelloWorld()

        then:
        runTasks(*tasks)
        dependencies(buildFile, 'implementation') == ['com.google.guava:guava']
    }

    def dependencies(File _buildFile, String... confs = ['compile', 'testCompile', 'implementation', 'testImplementation', 'api']) {
        _buildFile.text.readLines()
                .collect { it.trim() }
                .findAll { line -> confs.any { c -> line.startsWith(c) } }
                .collect { it.split(/\s+/)[1].replaceAll(/['"]/, '') }
                .sort()
    }
}
