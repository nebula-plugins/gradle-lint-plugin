package com.netflix.nebula.lint.rule

class UnusedExcludeByConfigurationRuleSpec extends AbstractRuleSpec {
    def 'unused exclude violates'() {
        when:
        // trivial case: no dependencies
        project.buildFile << """
            apply plugin: 'java'
            configurations {
                all*.exclude group: 'com.google.guava', module: 'guava'
            }
        """

        project.apply plugin: 'java'

        def results = runRulesAgainst(new UnusedExcludeByConfigurationRule(project: project))

        then:
        results.violates(UnusedExcludeByConfigurationRule)
    }

    def 'exclude matching a transitive dependency does not violate'() {
        when:
        project.buildFile << """
            configurations {
                compile.exclude group: 'commons-logging', module: 'commons-logging'
                all*.exclude group: 'commons-lang', module: 'commons-lang'
            }
        """

        project.with {
            apply plugin: 'java'
            repositories { mavenCentral() }
            configurations {
                compile.exclude group: 'commons-logging', module: 'commons-logging'
                compile.exclude group: 'commons-lang', module: 'commons-lang'
            }
            dependencies {
                compile 'commons-configuration:commons-configuration:1.10'
            }
        }

        def results = runRulesAgainst(new UnusedExcludeByConfigurationRule(project: project))

        then:
        results.doesNotViolate(UnusedExcludeByConfigurationRule)
    }
}
