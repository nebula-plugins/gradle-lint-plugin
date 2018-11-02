package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.BuildFiles
import com.netflix.nebula.lint.rule.GradleDependency
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification
import spock.lang.Subject

@Subject(DependencyViolationUtil)
class DependencyViolationUtilSpec extends Specification {

    @Rule
    final TestName testName = new TestName()

    def 'replaces dependency configuration - single line'() {
        setup:
        GradleViolation mockViolation = Mock(GradleViolation)
        MethodCallExpression mockMethodCallExpression = Mock(MethodCallExpression)
        GradleDependency mockGradleDependency = Mock(GradleDependency)

        when:
        DependencyViolationUtil.replaceDependencyConfiguration(mockViolation, mockMethodCallExpression, "implementation", mockGradleDependency)

        then:
        1 * mockViolation.replaceWith(mockMethodCallExpression, "implementation 'example:foo:1.0.0'")
        1 * mockGradleDependency.toNotation() >> 'example:foo:1.0.0'
    }

    def 'replaces dependency configuration - multi line'() {
        setup:
        File projectDir = new File("build/nebulatest/${this.class.canonicalName}/${testName.methodName.replaceAll(/\W+/, '-')}").absoluteFile
        if (projectDir.exists()) {
            projectDir.deleteDir()
        }
        projectDir.mkdirs()
        File buildFile = new File(projectDir, "build.gradle")
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java-library'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                compile('example:foo:1.0.0') {
                    exclude module: 'spring-boot-starter-tomcat'
                }
            }
        """
        BuildFiles buildFiles = new BuildFiles([buildFile])
        GradleViolation mockViolation = Mock(GradleViolation)
        MethodCallExpression mockMethodCallExpression = Mock(MethodCallExpression)

        when:
        DependencyViolationUtil.replaceDependencyConfiguration(mockViolation, mockMethodCallExpression, 'implementation')

        then:
        1 * mockViolation.getFiles() >> buildFiles
        1 * mockMethodCallExpression.getLineNumber() >> 13
        1 * mockMethodCallExpression.getLastLineNumber() >> 16
        1 * mockViolation.replaceWith(mockMethodCallExpression, "dependencies {\n                implementation(\'example:foo:1.0.0\') {\n                    exclude module: \'spring-boot-starter-tomcat\'\n                }")
        1 * mockMethodCallExpression.getMethodAsString() >> 'compile'
    }

    def 'replaces project dependency configuration'() {
        setup:
        GradleViolation mockViolation = Mock(GradleViolation)
        MethodCallExpression mockMethodCallExpression = Mock(MethodCallExpression)

        when:
        DependencyViolationUtil.replaceProjectDependencyConfiguration(mockViolation, mockMethodCallExpression, "implementation", ":sub1")

        then:
        1 * mockViolation.replaceWith(mockMethodCallExpression, "implementation project(':sub1')")
    }

}
