package com.netflix.nebula.lint.plugin

import nebula.test.IntegrationTestKitSpec
import spock.lang.Issue

class GradleLintReportTaskSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
    }

    def 'generate a report'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint {
                rules = ['dependency-parentheses']
                reportFormat = 'text'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasks('generateGradleLintReport')

        when:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }

        then:
        report.text.contains('Violation: Rule=dependency-parentheses')
        report.text.contains('TotalFiles=1')
    }

    def 'critical rules fail task'() {
        when:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.criticalRules = ['dependency-parentheses']

            repositories { mavenCentral() }

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        then:
        def results = runTasksAndFail('generateGradleLintReport')
        results.output.contains('FAIL')
        results.output.contains('Task failed with an exception')
    }

    def 'generate a report for a multi-module project'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }
        """
        
        def sub = addSubproject('sub')
        new File(sub, 'build.gradle').text = """
            plugins {
                id 'java'
            }

            gradleLint {
                rules = ['dependency-parentheses']
                reportFormat = 'text'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasks('generateGradleLintReport')

        when:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }
        
        then:
        report.text.contains('Violation: Rule=dependency-parentheses')
        report.text.contains('TotalFiles=1')
    }

    @Issue('#137')
    def 'generate a report for a DependencyService based rule'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint {
                rules = ['duplicate-dependency-class']
                reportFormat = 'text'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'com.google.guava:guava:18.0'
                implementation 'com.google.collections:google-collections:1.0'
            }
        """

        then:
        runTasks('generateGradleLintReport')
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }

        then:
        report.text.contains('Violation: Rule=duplicate-dependency-class')
        report.text.contains('TotalFiles=1')
    }
}
