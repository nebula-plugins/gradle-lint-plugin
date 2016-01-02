package com.netflix.nebula.lint.rule.rename

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class PluginRenamedRuleSpec extends AbstractRuleSpec {
    def 'deprecated plugin names are recorded as violations'() {
        when:
        project.buildFile << """
            apply plugin: 'ye-olde-plugin'
        """

        def results = runRulesAgainst(new PluginRenamedRule('renamed', 'ye-olde-plugin', 'shiny-new-plugin'))

        then:
        results.violations.size() == 1
    }

    def 'deprecated plugin names are replaced with new names'() {
        when:
        project.buildFile << """
            apply plugin: 'ye-olde-plugin'
        """

        def corrected = correct(new PluginRenamedRule('renamed', 'ye-olde-plugin', 'shiny-new-plugin'))

        then:
        corrected == """
            apply plugin: 'shiny-new-plugin'
        """
    }

    def 'concrete implementation of PluginRenamedRule'() {
        when:
        project.buildFile << """
            apply plugin: 'gradle-dependency-lock'
        """

        def results = runRulesAgainst(new RenameNebulaDependencyLockRule())

        then:
        results.violations.size() == 1
    }
}
