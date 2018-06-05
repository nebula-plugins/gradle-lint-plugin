package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import nebula.test.IntegrationSpec
import org.codehaus.groovy.ast.expr.MethodCallExpression

class FixGradleLintTaskCriticalRulesSpec extends IntegrationSpec {
    def 'critical lint violations include cases requiring user action'() {
        given:
        def lintRuleDirectory = new File(projectDir, 'src/main/resources/META-INF/lint-rules')
        lintRuleDirectory.mkdirs()

        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            repositories {
                mavenCentral()
            }

            gradleLint.criticalRules = ['test-user-action-required']
            
            dependencies {
                compile('commons-lang:commons-lang:2.6')
            }
        """

        when:
        writeHelloWorld('test.nebula')

        then:
        def results = runTasksWithFailure('compileJava', 'fixGradleLint', '-s')
        
        //we need to check both stream because Gradle 4.8 changes where error are printed, during transition period
        //we run tests or multiple version so we need to maintain compatibility
        def expectedMessage = 'This build contains 1 critical lint violation'
        results.standardOutput.contains(expectedMessage) || results.standardError.contains(expectedMessage)

        def expectedRuleFailure = 'needs fixing.*test-user-action-required'
        results.standardOutput.findAll(expectedRuleFailure).size() == 1 ||
                results.standardError.findAll(expectedRuleFailure).size() == 1
    }
}

class UserActionRequiredExampleRule extends GradleLintRule implements GradleModelAware {
    String description = 'example rule that requires a user action'

    @Override
    void visitDependencies(MethodCallExpression call) {
        addBuildLintViolation("$description", call)
    }
}