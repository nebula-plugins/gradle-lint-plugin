package com.netflix.nebula.lint.rule.plugin

import nebula.test.IntegrationTestKitSpec
import spock.lang.Issue
import spock.lang.Unroll

class GradleLintReportTaskSpec extends IntegrationTestKitSpec {

    def 'generate a report with different type through parameter from cli with Gradle version 4.10.3'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint {
                rules = ['dependency-parentheses']
            }

            repositories { mavenCentral() }

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        gradleVersion = '4.10.3'

        then:
        runTasks('generateGradleLintReport', '-PgradleLint.reportFormat=text')

        when:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }

        then:
        report.text.contains('Violation: Rule=dependency-parentheses')
        report.text.contains('TotalFiles=1')
    }
}
