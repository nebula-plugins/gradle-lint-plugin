package com.netflix.nebula.lint.rule.dependency


import nebula.test.IntegrationTestKitSpec
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf
import spock.lang.Subject
import spock.lang.Unroll

@IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
@Unroll
@Subject(DeprecatedDependencyConfigurationRule)
class DeprecatedDependencyConfigurationRuleSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
    }

    def 'Replaces deprecated configurations - #configuration for #replacementConfiguration'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration 'com.google.guava:guava:19.0'
            }
        """

        when:
        def result = runTasks('autoLintGradle', '--warning-mode=none')

        then:
        result.output.contains("warning   deprecated-dependency-configurationConfiguration $configuration has been deprecated and should be replaced with $replacementConfiguration (no auto-fix available)")

        where:
        configuration | replacementConfiguration
        "compile"     | "implementation"
        "testCompile" | "testImplementation"
        "runtime"     | "runtimeOnly"
    }

    def 'Replaces deprecated configurations - multi project - project dependency - #configuration for #replacementConfiguration'() {
        def sub1 = addSubproject('sub1', """
            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration 'com.google.guava:guava:19.0'
                $configuration project(':sub2')
            }

            def x = "test"
            """.stripIndent())

        writeJavaSourceFile('public class Sub1 {}', sub1)

        def sub2 = addSubproject('sub2', """            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration 'com.google.guava:guava:19.0'
            }
            """.stripIndent())

        writeJavaSourceFile('public class Sub2 {}', sub2)

        buildFile << """
            plugins {
                id 'nebula.lint'
            }
            
            subprojects {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
            }
              
            allprojects {
                  gradleLint.rules = ['deprecated-dependency-configuration']
            }

        """

        when:
        def result = runTasks('autoLintGradle', '--warning-mode=none')

        then:
        result.output.contains("warning   deprecated-dependency-configurationConfiguration $configuration has been deprecated and should be replaced with $replacementConfiguration (no auto-fix available)")

        where:
        configuration | replacementConfiguration
        "compile"     | "implementation"
        "testCompile" | "testImplementation"
        "runtime"     | "runtimeOnly"
    }


    def 'Replaces deprecated configuration - latest release - #configuration for #replacementConfiguration'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration 'org.apache.tomcat:tomcat-catalina:latest.release'
            }
        """

        when:
        def result = runTasks('autoLintGradle', '--warning-mode=none')

        then:
        result.output.contains("warning   deprecated-dependency-configurationConfiguration $configuration has been deprecated and should be replaced with $replacementConfiguration (no auto-fix available)")

        where:
        configuration | replacementConfiguration
        "compile"     | "implementation"
        "testCompile" | "testImplementation"
        "runtime"     | "runtimeOnly"
    }

    def 'Replaces deprecated configuration - dynamic version  - #configuration for #replacementConfiguration'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration 'org.apache.tomcat:tomcat-catalina:7.+'
            }
        """

        when:
        def result = runTasks('autoLintGradle', '--warning-mode=none')

        then:
        result.output.contains("warning   deprecated-dependency-configurationConfiguration $configuration has been deprecated and should be replaced with $replacementConfiguration (no auto-fix available)")

        where:
        configuration | replacementConfiguration
        "compile"     | "implementation"
        "testCompile" | "testImplementation"
        "runtime"     | "runtimeOnly"
    }

    def 'Replaces deprecated configuration - with excludes - #configuration for #replacementConfiguration'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration('org.apache.tomcat:tomcat-catalina:latest.release') {
                    exclude module: "spring-boot-starter-tomcat"
                }
            }
        """

        when:
        def result = runTasks('autoLintGradle', '--warning-mode=none')

        then:
        result.output.contains("warning   deprecated-dependency-configurationConfiguration $configuration has been deprecated and should be replaced with $replacementConfiguration (no auto-fix available)")

        where:
        configuration | replacementConfiguration
        "compile"     | "implementation"
        "testCompile" | "testImplementation"
        "runtime"     | "runtimeOnly"
    }
}
