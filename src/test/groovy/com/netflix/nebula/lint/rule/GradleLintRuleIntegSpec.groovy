package com.netflix.nebula.lint.rule


import nebula.test.IntegrationTestKitSpec
import spock.lang.Issue

class GradleLintRuleIntegSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
    }

    @Issue('#67')
    def 'find dependencies that are provided by extension properties'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.criticalRules = ['unused-dependency']

            repositories { mavenCentral() }

            ext.deps = [ guava: 'com.google.guava:guava:19.0' ]

            dependencies {
                implementation deps.guava
            }
        """

        when:
        writeHelloWorld()

        then:
        def result = runTasksAndFail('compileJava', 'lintGradle')
        result.output.contains('this dependency is unused and can be removed')
    }

    @Issue('#65')
    def 'find dependencies that are provided by interpolated strings'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.criticalRules = ['unused-dependency']

            repositories { mavenCentral() }

            def v = 'latest.release'
            dependencies {
                implementation "com.google.guava:guava:\$v"
                implementation group: 'commons-lang', name: 'commons-lang', version: "\$v"
            }
        """

        when:
        writeHelloWorld()

        then:
        def result = runTasksAndFail('compileJava', 'lintGradle')
        result.output.contains('this dependency is unused and can be removed')
    }
}
