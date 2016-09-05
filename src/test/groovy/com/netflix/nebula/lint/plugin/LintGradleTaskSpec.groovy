package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.TestKitSpecification

class LintGradleTaskSpec extends TestKitSpecification {

    def 'mark violations that have no auto-remediation'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                compile 'com.google.guava:guava:18.0'
                compile 'com.google.collections:google-collections:1.0'
            }
        """

        when:
        createJavaSourceFile('public class Main {}')
        def result = runTasksSuccessfully('compileJava', 'lintGradle')

        then:
        result.output.contains('(no auto-fix available)')
    }
}
