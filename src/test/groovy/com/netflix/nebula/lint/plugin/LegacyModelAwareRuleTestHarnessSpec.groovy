package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class LegacyModelAwareRuleTestHarnessSpec extends AbstractRuleSpec {
    def 'rule test harness supplies the evaluated project to legacy model-aware rules'() {
        given:
        project.ext.minimumRenameGradleVersion = '7.0'
        project.buildFile.text = "apply plugin: 'base'\n"

        when:
        def corrected = correct(new LegacyModelAwareRenameRule())

        then:
        corrected == "apply plugin: 'java'\n"
    }
}
