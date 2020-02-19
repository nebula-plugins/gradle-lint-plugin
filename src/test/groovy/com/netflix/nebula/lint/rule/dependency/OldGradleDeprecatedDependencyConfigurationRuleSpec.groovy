package com.netflix.nebula.lint.rule.dependency


import nebula.test.IntegrationTestKitSpec
import spock.lang.IgnoreIf
import spock.lang.Subject
import spock.lang.Unroll

@Unroll
@Subject(DeprecatedDependencyConfigurationRule)
@IgnoreIf({ jvm.isJava9Compatible() })
class OldGradleDeprecatedDependencyConfigurationRuleSpec extends IntegrationTestKitSpec {

    //Should not replace configurations when running with gradle < 4.7
    def setup() {
        gradleVersion = "4.6"
    }

    def 'does not replace deprecated configurations - #configuration'() {
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
        def result = runTasks('fixGradleLint', '--warning-mode=none')

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

    def 'does not replacer deprecated configurations - multi project - project dependency - #configuration'() {
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

        writeHelloWorld(sub1)

        def sub2 = addSubproject('sub2', """            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                $configuration 'com.google.guava:guava:19.0'
            }
            """.stripIndent())

        writeHelloWorld(sub2)

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
        def result = runTasks('fixGradleLint', '--warning-mode=none')

        then:
        def sub1BuildGradle = new File(projectDir, 'sub1/build.gradle')
        sub1BuildGradle.text.trim() == """
repositories {
    mavenCentral()
}

dependencies {
    $configuration 'com.google.guava:guava:19.0'
    $configuration project(':sub2')
}

def x = "test"
        """.trim()

        where:
        configuration << ["compile", "testCompile", "runtime"]
    }


    def 'does not replace deprecated configuration - latest release - #configuration'() {
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
        def result = runTasks('fixGradleLint', '--warning-mode=none')

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

    def 'does not replace deprecated configuration  - dynamic version  - #configuration'() {
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
        def result = runTasks('fixGradleLint', '--warning-mode=none')

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

    def 'does not replace deprecated configuration  - with excludes - #configuration'() {
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
        def result = runTasks('fixGradleLint', '--warning-mode=none')

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
