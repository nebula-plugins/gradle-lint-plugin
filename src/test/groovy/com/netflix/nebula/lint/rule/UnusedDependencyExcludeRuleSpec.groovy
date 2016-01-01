package com.netflix.nebula.lint.rule

class UnusedDependencyExcludeRuleSpec extends AbstractRuleSpec {
    def 'unused exclude violates'() {
        when:
        // trivial case: no dependencies
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'com.google.guava', module: 'guava'
                }
            }
        }

        def results = runRulesAgainst(new UnusedDependencyExcludeRule(project: project))

        then:
        results.violates(UnusedDependencyExcludeRule)
    }

    def 'exclude matching a transitive dependency does not violate'() {
        when:
        // trivial case: no dependencies
        project.buildFile << """
            apply plugin: 'java'
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'commons-lang', module: 'commons-lang'
                }
            }
        """

        project.with {
            apply plugin: 'java'
            repositories {
                mavenCentral()
            }
            dependencies {
                compile('commons-configuration:commons-configuration:1.10') {
                    exclude group: 'commons-lang', module: 'commons-lang'
                }
            }
        }

        def results = runRulesAgainst(new UnusedDependencyExcludeRule(project: project))

        then:
        results.doesNotViolate(UnusedDependencyExcludeRule)
    }
}
