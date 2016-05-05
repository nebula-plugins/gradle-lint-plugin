package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.TestKitSpecification

class FixGradleLintTaskSpec extends TestKitSpecification {

    def 'overlapping patches result in unfixed or semi-fixed results'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency', 'dependency-parentheses']

            repositories { mavenCentral() }

            dependencies {
                compile('com.google.guava:guava:18.0')
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def results = runTasksSuccessfully('compileJava', 'fixGradleLint')

        results.output.count('fixed          unused-dependency') == 1
        results.output.count('unfixed        dependency-parentheses') == 1
    }
}
