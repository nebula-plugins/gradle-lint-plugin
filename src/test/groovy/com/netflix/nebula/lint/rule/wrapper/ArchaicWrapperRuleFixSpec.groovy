package com.netflix.nebula.lint.rule.wrapper

import com.netflix.nebula.lint.TestKitSpecification

class ArchaicWrapperRuleFixSpec extends TestKitSpecification {
    def 'wrapper without a gradleVersion property has one inserted'() {
        when:
        buildFile.text = """\
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['archaic-wrapper']

            task wrapper(type: Wrapper) {
            }
            """.stripMargin()

        then:
        runTasksSuccessfully('compileJava', 'fixGradleLint')
        buildFile.text.contains('gradleVersion =')
    }
}
