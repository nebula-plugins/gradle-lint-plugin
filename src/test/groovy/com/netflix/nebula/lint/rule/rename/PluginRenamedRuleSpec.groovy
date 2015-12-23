package com.netflix.nebula.lint.rule.rename

import com.netflix.nebula.lint.rule.AbstractRuleSpec

class PluginRenamedRuleSpec extends AbstractRuleSpec {
    def 'deprecated plugin names are recorded as violations'() {
        when:
        def results = runRulesAgainst("""
            apply plugin: 'ye-olde-plugin'
        """, new PluginRenamedRule('renamed', 'ye-olde-plugin', 'shiny-new-plugin'))

        then:
        results.violations.size() == 1
    }

    def 'deprecated plugin names are replaced with new names'() {
        when:
        def corrected = correct("""
            apply plugin: 'ye-olde-plugin'
        """, new PluginRenamedRule('renamed', 'ye-olde-plugin', 'shiny-new-plugin'))

        then:
        corrected == """
            apply plugin: 'shiny-new-plugin'
        """
    }

    def 'concrete implementation of PluginRenamedRule'() {
        when:
        def results = runRulesAgainst("""
            apply plugin: 'gradle-dependency-lock'
        """, new RenameNebulaDependencyLockRule())

        then:
        results.violations.size() == 1
    }
}
