package com.netflix.nebula.lint.rule.plugin

import nebula.test.IntegrationTestKitSpec
import spock.lang.IgnoreIf

@IgnoreIf({ jvm.isJava17Compatible() }) // Because we use old version of Gradle and kotlin
class FixGradleLintTaskSpec extends IntegrationTestKitSpec {
    /**
     * Because Gradle changed the internal APIs we are using to performed stylized text logging...
     * This verifies that our reflection hack continues to be backwards compatible
     */
    @IgnoreIf({ jvm.isJava9Compatible() })
    def 'Make sure logging works on older gradle version'() {
        buildFile << """\
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['all-dependencies']
            """.stripIndent()
        gradleVersion = '4.2' //we don't support older versions anymore

        when:
        def results = runTasks('assemble', 'lintGradle')

        then:
        println results?.output
        noExceptionThrown()
    }
}
