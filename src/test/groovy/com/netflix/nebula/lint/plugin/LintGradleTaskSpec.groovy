package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.TestKitSpecification
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildResultException

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

    def 'critical rule failures cause build failure'() {
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
        runTasksFail('lintGradle')
    }
}
