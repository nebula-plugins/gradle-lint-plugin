package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import com.netflix.nebula.lint.GradleVersions
import com.netflix.nebula.lint.rule.AbstractExampleGradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import spock.lang.Unroll

class LegacyModelAwareRuleSpec extends BaseIntegrationTestKitSpec {
    @Unroll
    def 'legacy rule uses its owning project and preserves the version gate on Gradle #version'() {
        given:
        gradleVersion = version
        definePluginOutsideOfPluginBlock = true
        disableConfigurationCache()
        buildFile << '''
            apply plugin: 'nebula.lint'
            ext.minimumRenameGradleVersion = '7.0'
            gradleLint.rules = ['test-legacy-model-aware-rename']
            apply plugin: 'base'
        '''.stripIndent()
        File child = new File(addSubproject('child', '''
            ext.minimumRenameGradleVersion = '999.0'
            apply plugin: 'base'
        '''.stripIndent()), 'build.gradle')
        String originalChild = child.text
        String expectedRoot = buildFile.text.replace("apply plugin: 'base'", "apply plugin: 'java'")

        when:
        def first = runTasks('fixGradleLint')

        then:
        first.task(':fixGradleLint').outcome == TaskOutcome.SUCCESS
        buildFile.text == expectedRoot
        child.text == originalChild

        when:
        def second = runTasks('fixGradleLint')

        then:
        second.task(':fixGradleLint').outcome == TaskOutcome.SUCCESS
        buildFile.text == expectedRoot
        child.text == originalChild

        where:
        version << GradleVersions.ALL
    }
}

// This is the API shape used by rules compiled before ModelAwareGradleLintRule was introduced.
class LegacyModelAwareRenameRule extends AbstractExampleGradleLintRule implements GradleModelAware {
    String description = 'rename a plugin when the evaluated project permits it'

    @Override
    void visitApplyPlugin(MethodCallExpression call, String plugin) {
        def current = GradleVersion.version(project.gradle.gradleVersion)
        def minimum = GradleVersion.version(project.property('minimumRenameGradleVersion').toString())
        if (current >= minimum && plugin == 'base') {
            addBuildLintViolation(description, call).replaceWith(call, "apply plugin: 'java'")
        }
    }
}
