package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
@Subject(DeprecatedDependencyConfigurationRule)
class OldGradleDeprecatedDependencyConfigurationRuleSpec extends TestKitSpecification {

    //Should not replace with gradle < 4.7
    def setup() {
        gradleVersion = "4.6"
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
                $configuration 'com.google.guava:guava:19.0'
            }
        """.trim()

        where:
        configuration << ["compile", "testCompile", "runtime"]

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

        createJavaSourceFile(sub1, 'public class Sub1 {}')

        def sub2 = addSubproject('sub2', """            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration 'com.google.guava:guava:19.0'
            }
            """.stripIndent())

        createJavaSourceFile(sub2, 'public class Sub2 {}')

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
        def result = runTasksSuccessfully('fixGradleLint', '--warning-mode=none')

        then:
        def sub1BuildGradle = new File(projectDir, 'sub1/build.gradle')
        sub1BuildGradle.text.trim() == """
repositories {
    mavenCentral()
}

dependencies {
    $replacementConfiguration 'com.google.guava:guava:19.0'
    $replacementConfiguration project(':sub2')
}

def x = "test"
        """.trim()

        where:
        configuration | replacementConfiguration
        "compile"     | "compile"
        "testCompile" | "testCompile"
        "runtime"     | "runtime"
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
                $configuration 'org.apache.tomcat:tomcat-catalina:latest.release'
            }
        """.trim()

        where:
        configuration << ["compile", "testCompile", "runtime"]

    }

    def 'Replaces deprecated configuration  - dynamic version  - #configuration for #replacementConfiguration'() {
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
                $configuration 'org.apache.tomcat:tomcat-catalina:7.+'
            }
        """.trim()

        where:
        configuration << ["compile", "testCompile", "runtime"]

    }

    def 'Replaces deprecated configuration  - with excludes - #configuration for #replacementConfiguration'() {
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
                $configuration('org.apache.tomcat:tomcat-catalina:latest.release') {
                    exclude module: "spring-boot-starter-tomcat"
                }
            }
        """.trim()

        where:
        configuration << ["compile", "testCompile", "runtime"]

    }
}
