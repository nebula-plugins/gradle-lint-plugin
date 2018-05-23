package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Issue

class GradleLintRuleIntegSpec extends TestKitSpecification {
    @Issue('#67')
    def 'find dependencies that are provided by extension properties'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.criticalRules = ['unused-dependency']

            repositories { mavenCentral() }

            ext.deps = [ guava: 'com.google.guava:guava:19.0' ]

            dependencies {
                compile deps.guava
            }
        """

        when:
        createJavaSourceFile('public class Main{}')

        then:
        def result = runTasksFail('compileJava', 'lintGradle')
        result.output.contains('this dependency is unused and can be removed')
    }

    @Issue('#65')
    def 'find dependencies that are provided by interpolated strings'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.criticalRules = ['unused-dependency']

            repositories { mavenCentral() }

            def v = 'latest.release'
            dependencies {
                compile "com.google.guava:guava:\$v"
                compile group: 'commons-lang', name: 'commons-lang', version: "\$v"
            }
        """

        when:
        createJavaSourceFile('public class Main{}')

        then:
        def result = runTasksFail('compileJava', 'lintGradle')
        result.output.contains('this dependency is unused and can be removed')
    }
}
