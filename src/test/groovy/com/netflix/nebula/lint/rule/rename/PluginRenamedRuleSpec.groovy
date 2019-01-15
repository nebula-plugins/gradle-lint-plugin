/*
 * Copyright 2015-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule.rename

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec

class PluginRenamedRuleSpec extends AbstractRuleSpec {
    def 'deprecated plugin names are recorded as violations'() {
        when:
        project.buildFile << """
            apply plugin: 'ye-olde-plugin'
        """

        def results = runRulesAgainst(new PluginRenamedRule('ye-olde-plugin', 'shiny-new-plugin'))

        then:
        results.violations.size() == 1
    }

    def 'deprecated plugin names (using plugins DSL) are recorded as violations'() {
        when:
        project.buildFile << """
            plugins {
             id 'ye-olde-plugin'
            }
        """

        def results = runRulesAgainst(new PluginRenamedRule('ye-olde-plugin', 'shiny-new-plugin'))

        then:
        results.violations.size() == 1
    }
    def 'deprecated plugin names are replaced with new names'() {
        when:
        project.buildFile << """
            apply plugin: 'ye-olde-plugin'
        """

        def corrected = correct(new PluginRenamedRule('ye-olde-plugin', 'shiny-new-plugin'))

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
