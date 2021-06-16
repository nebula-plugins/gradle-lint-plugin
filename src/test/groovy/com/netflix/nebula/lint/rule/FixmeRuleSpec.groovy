package com.netflix.nebula.lint.rule

import nebula.test.IntegrationTestKitSpec
import spock.lang.Subject

@Subject(FixmeRule)
class FixmeRuleSpec extends IntegrationTestKitSpec {
    def tasks = ['assemble', 'fixGradleLint']

    def setup() {
        def oldDate = '2010-12-1'
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java-library'
            }
            gradleLint.rules = ['minimum-dependency-version']
            repositories { mavenCentral() }
            dependencies {
                gradleLint.fixme('$oldDate') {
                    implementation 'com.google.guava:guava:18.+'
                }
            }
        """.stripIndent()
    }

    def 'expired fixmes are critical violations'() {
        expect:
        def result = runTasksAndFail(*tasks)
        result.output.contains('needs fixing')
        result.output.contains('critical lint violation')
    }

    def 'expired fixmes are noncritical violations when a property is used'() {
        expect:
        def result = runTasks(*tasks, '-Dnebula.lint.fixmeAsNonCritical=true')
        result.output.contains('needs fixing')
        !result.output.contains('critical lint violation')
    }
}
