package com.netflix.nebula.lint.rule

import nebula.test.IntegrationSpec
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.logging.LogLevel

class LintRuleApplicationFailureContextSpec extends IntegrationSpec {
    def setup() {
        def lintRuleDirectory = new File(projectDir, 'src/main/resources/META-INF/lint-rules')
        lintRuleDirectory.mkdirs()

        buildFile << """
            plugins {
                id 'java-library'
            }
            apply plugin: 'nebula.lint'
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation('commons-lang:commons-lang:2.6')
            }
            """.stripIndent()

        logLevel = LogLevel.LIFECYCLE
    }

    def 'lint rule application failure with extra context'() {
        given:
        buildFile << """
            gradleLint.rules = ['test-fails-to-apply-successfully-with-extra-context']
            """.stripIndent()

        when:
        def results = runTasksWithFailure('fixGradleLint')

        then:
        results.standardError.contains("Error processing rule Lint Rule 'test-fails-to-apply-successfully-with-extra-context'. " +
                "Here is some extra context about what to do when you see this unexpected failure")
    }

    def 'default lint rule application failure messaging'() {
        given:
        buildFile << """
            gradleLint.rules = ['test-fails-to-apply-successfully']
            """.stripIndent()

        when:
        def results = runTasksWithFailure('fixGradleLint')

        then:
        results.standardError.contains("Error processing rule Lint Rule 'test-fails-to-apply-successfully'\n")
    }
}

class ExampleRuleWhichFailsToApplySuccessfully extends AbstractModelAwareExampleGradleLintRule {
    String description = 'example rule that fails to apply successfully with no extra context'

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        throw new RuntimeException("testing when something goes wrong")
    }
}

class ExampleRuleWhichFailsToApplySuccessfullyWithExtraContext extends AbstractModelAwareExampleGradleLintRule {
    String description = 'example rule that fails to apply successfully with some extra context'

    @Override
    protected String ruleFailureContext() {
        return "Here is some extra context about what to do when you see this unexpected failure"
    }

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        throw new RuntimeException("testing when something goes wrong")
    }
}
