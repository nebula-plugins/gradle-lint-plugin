package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Issue

class GradleLintReportTaskSpec extends TestKitSpecification {

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
                compile('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasksSuccessfully('generateGradleLintReport')

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
                compile('com.google.guava:guava:18.0')
            }
        """

        then:
        def results = runTasksFail('generateGradleLintReport')
        println results.output
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
                compile('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasksSuccessfully('generateGradleLintReport')

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
                compile 'com.google.guava:guava:18.0'
                compile 'com.google.collections:google-collections:1.0'
            }
        """

        then:
        runTasksSuccessfully('generateGradleLintReport')
    }
}
