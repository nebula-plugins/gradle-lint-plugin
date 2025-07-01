
package com.netflix.nebula.lint



class ConfigurationCacheCompatibilityTest extends BaseIntegrationTestKitSpec {
    def test() {
        buildFile << """
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            gradleLint.rules = ['dependency-parentheses']
            dependencies {
                implementation('junit:junit:4.11')
            }
        """
        keepFiles = true
        forwardOutput = true

        when:
        def result = runTasks('autoLintGradle', '--warning-mode', 'none')

        then:
        result.output.contains("1 problems (0 errors, 1 warning)")
    }
}