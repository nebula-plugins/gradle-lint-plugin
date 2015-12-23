package com.netflix.nebula.lint.plugin

import nebula.test.IntegrationSpec

class GradleLintPluginSpec extends IntegrationSpec {
    def 'run multiple rules'() {
        when:
        buildFile << """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            lint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasksSuccessfully('lint')

        println results.standardOutput
    }
}
