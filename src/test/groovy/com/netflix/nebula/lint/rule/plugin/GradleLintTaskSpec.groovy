package com.netflix.nebula.lint.rule.plugin

import com.netflix.nebula.lint.plugin.GradleLintTask
import spock.lang.Specification

class GradleLintTaskSpec extends Specification {
    def 'merge by sum'() {
        when:
        def merged = GradleLintTask.mergeBySum(
                [warning: 0, error: 0],
                [warning: 1, error: 2])

        then:
        merged.warning == 1
        merged.error == 2

        when:
        merged = GradleLintTask.mergeBySum(
                [warning: 1, error: 2],
                [warning: 0, error: 0])

        then:
        merged.warning == 1
        merged.error == 2
    }
}
