package com.netflix.nebula.lint.rule.plugin

import nebula.test.IntegrationTestKitSpec
import spock.lang.IgnoreIf
import spock.lang.Issue

@IgnoreIf({ jvm.isJava17Compatible() })
class Gradle6LintReportTaskSpec extends IntegrationTestKitSpec {
    def setup() {
        gradleVersion = '6.9'
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

    def 'generate a report with only applied fixes'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint {
                rules = ['dependency-tuple', 'dependency-parentheses']
                reportFormat = 'text'
                reportOnlyFixableViolations = true
            }

            repositories { mavenCentral() }

            dependencies {
                implementation(group: 'com.google.guava', name: 'guava', version: '18.0')
            }
        """

        when:
        def result = runTasks('autoLintGradle')

        then:
        result.output.contains("dependency-tuple")
        result.output.contains("dependency-parentheses")

        then:
        runTasks('generateGradleLintReport')

        when:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }

        then:
        report.text.contains('Violation: Rule=dependency-tuple')
        ! report.text.contains('Violation: Rule=dependency-parentheses')
        report.text.contains('TotalFiles=1')
    }

    def 'generate a report with different type through parameter from cli'() {
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

        then:
        runTasks('generateGradleLintReport', '-PgradleLint.reportFormat=text')

        when:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }

        then:
        report.text.contains('Violation: Rule=dependency-parentheses')
        report.text.contains('TotalFiles=1')
    }

    def 'generate a report with only applied fixes using cli param to enable it'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint {
                rules = ['dependency-tuple', 'dependency-parentheses']
                reportFormat = 'text'
            }

            repositories { mavenCentral() }

            dependencies {
                implementation(group: 'com.google.guava', name: 'guava', version: '18.0')
            }
        """

        when:
        runTasks('generateGradleLintReport', '-PgradleLint.reportOnlyFixableViolations=true')

        then:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }
        report.text.contains('Violation: Rule=dependency-tuple')
        ! report.text.contains('Violation: Rule=dependency-parentheses')
        report.text.contains('TotalFiles=1')
    }

    def 'generate a report with all rules using cli param to disable it to override extension'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint {
                rules = ['dependency-tuple', 'dependency-parentheses']
                reportFormat = 'text'
                reportOnlyFixableViolations = true
            }

            repositories { mavenCentral() }

            dependencies {
                implementation(group: 'com.google.guava', name: 'guava', version: '18.0')
            }
        """

        when:
        runTasks('generateGradleLintReport', '-PgradleLint.reportOnlyFixableViolations=false')

        then:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }
        report.text.contains('Violation: Rule=dependency-tuple')
        report.text.contains('Violation: Rule=dependency-parentheses')
        report.text.contains('TotalFiles=1')
    }

    def 'warning without autofixes are not reported if flag is enabled'() {
        given:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint {
                rules = ['duplicate-dependency-class']
                reportFormat = 'text'
                reportOnlyFixableViolations = true
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'com.google.guava:guava:18.0'
                implementation 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        runTasks('generateGradleLintReport')

        then:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.txt') }
        ! report.text.contains('Violation: Rule=duplicate-dependency-class')
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

    def 'generate a xml report for a multi-module project'() {
        when:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            allprojects {
                gradleLint {
                    rules = ['dependency-parentheses']
                    reportFormat = 'xml'
                }
                
                repositories { mavenCentral() }
            }
            
            dependencies {
                implementation('com.google.guava:guava:19.0')
            }
        """

        def sub = addSubproject('sub')
        new File(sub, 'build.gradle').text = """
            plugins {
                id 'java'
            }

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasks('generateGradleLintReport')

        when:
        def report = new File(projectDir, 'build/reports/gradleLint').listFiles().find { it.name.endsWith('.xml') }

        then:
        def reportXml = report.text
        reportXml.count('Violation ruleName=\'dependency-parentheses\'') == 2
        reportXml.contains('implementation(\'com.google.guava:guava:19.0\')')
        reportXml.contains('implementation(\'com.google.guava:guava:18.0\')')
        reportXml.contains("<Package path='$projectDir.absolutePath'")
        reportXml.contains("<Package path='$projectDir.absolutePath/sub'")
    }
}
