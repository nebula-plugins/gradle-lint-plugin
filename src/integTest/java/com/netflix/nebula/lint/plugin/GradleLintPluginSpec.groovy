package com.netflix.nebula.lint.plugin

import nebula.test.IntegrationSpec

class GradleLintPluginSpec extends IntegrationSpec {
    def 'run multiple rules on a single module project'() {
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

        when:
        def console = results.standardOutput.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
    }

    def 'auto correct all violations on a single module project'() {
        when:
        buildFile << """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            lint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasksSuccessfully('autoCorrectLint')

        buildFile.text.contains("""
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            lint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                compile 'com.google.guava:guava:18.0'
            }
        """.toString())
    }

    def 'rules relative to each project'() {
        when:
        buildFile << """
            allprojects {
                apply plugin: ${GradleLintPlugin.name}
                lint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    compile('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testCompile group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """)

        then:
        def results = runTasksSuccessfully('lint')

        println results.standardOutput
    }
}
