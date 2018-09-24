package com.netflix.nebula.lint.rule.wrapper

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Ignore

@Ignore("Wrapper task is not added by default to gradle builds. Builds with a task named wrapper will fail")
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
