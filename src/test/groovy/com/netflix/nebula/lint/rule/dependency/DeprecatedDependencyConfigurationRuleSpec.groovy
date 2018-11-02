package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
@Subject(DeprecatedDependencyConfigurationRule)
class DeprecatedDependencyConfigurationRuleSpec extends TestKitSpecification {

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
        def result = runTasksSuccessfully('fixGradleLint', '--warning-mode=none')

        then:
        def buildGradle = new File(projectDir, 'build.gradle')
        buildGradle.text.trim() == """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $replacementConfiguration 'com.google.guava:guava:19.0'
            }
        """.trim()

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
        def result = runTasksSuccessfully('fixGradleLint', '--warning-mode=none')

        then:
        def buildGradle = new File(projectDir, 'build.gradle')
        buildGradle.text.trim() == """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $replacementConfiguration 'org.apache.tomcat:tomcat-catalina:latest.release'
            }
        """.trim()

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
        def result = runTasksSuccessfully('fixGradleLint', '--warning-mode=none')

        then:
        def buildGradle = new File(projectDir, 'build.gradle')
        buildGradle.text.trim() == """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $replacementConfiguration 'org.apache.tomcat:tomcat-catalina:7.+'
            }
        """.trim()

        where:
        configuration | replacementConfiguration
        "compile"     | "implementation"
        "testCompile" | "testImplementation"
        "runtime"     | "runtimeOnly"
    }

    def 'Replaces deprecated dconfiguration - with excludes - #configuration for #replacementConfiguration'() {
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
        def result = runTasksSuccessfully('fixGradleLint', '--warning-mode=none')

        then:
        def buildGradle = new File(projectDir, 'build.gradle')
        buildGradle.text.trim() == """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-dependency-configuration']

            repositories {
                mavenCentral()
            }
            
            dependencies {
                $replacementConfiguration('org.apache.tomcat:tomcat-catalina:latest.release') {
                    exclude module: "spring-boot-starter-tomcat"
                }
            }
        """.trim()

        where:
        configuration | replacementConfiguration
        "compile"     | "implementation"
        "testCompile" | "testImplementation"
        "runtime"     | "runtimeOnly"
    }
}
