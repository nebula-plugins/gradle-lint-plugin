package com.netflix.nebula.lint.rule.dsl

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import spock.lang.Subject

@Subject(SpaceAssignmentRule)
class SpaceAssignmentRuleSpec extends BaseIntegrationTestKitSpec {

    def 'reports and fixes a violation if space assignment syntax is used - simple cases'() {
        buildFile << """
            import java.util.regex.Pattern;
            import org.gradle.internal.SystemProperties;
            
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            println(file("text"))
            getRepositories().gradlePluginPortal()
            
            buildDir file("out")
            
            repositories {
                'hello'
                maven {
                    url "https://example.com"
                }
                maven {
                    url("https://example2.com")
                }
                maven {
                    url = "https://another.example.com"
                }
                maven { url "https://another.example2.com" }
                maven { url 'https://repo.spring.io/milestone' }
             }
            
            java {
                sourceCompatibility JavaVersion.VERSION_1_8
            }
            gradleLint.rules = ['space-assignment']
            println(Pattern.quote("test"))
            SystemProperties i = SystemProperties.getInstance()
            i.getJavaIoTmpDir()
            
            group 'com.netflix.test'
            
            subprojects {
                group 'com.netflix.test'
            }
            
            allprojects {
                group 'com.netflix.test'
            }
            
            def matcher = ("test" =~ /ab[d|f]/)
            if (matcher.find()) {
                def x = matcher.group(1).replace(".", "/")
            }
            tasks.register('hello') {
              doLast {
                    if (matcher.find()) {
                    def x = matcher.group(1).replace(".", "/")
                }
              }
            }            
        """

        when:
        def result = runTasks('autoLintGradle', '--warning-mode', 'none')

        then:
        result.output.contains("9 problems (0 errors, 9 warnings)")

        when:
        runTasks('fixLintGradle', '--warning-mode', 'none', '--no-configuration-cache')

        then:
        buildFile.text.contains('buildDir = file("out")')
        buildFile.text.contains('url = "https://example.com"')
        buildFile.text.contains('url =("https://example2.com")')
        buildFile.text.contains('maven { url = "https://another.example2.com" }')
        !buildFile.text.contains('maven { maven { url = "https://another.example2.com" } }')
        buildFile.text.contains('sourceCompatibility = JavaVersion.VERSION_1_8')
        buildFile.text.contains('group = \'com.netflix.test\'')
        !buildFile.text.contains('group \'com.netflix.test\'')
        buildFile.text.contains('matcher.group(1)')
        !buildFile.text.contains('matcher.group = (1)')

        and:
        runTasks('help')
    }

    def 'reports and fixes a violation if space assignment syntax is used in some complex cases'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint.rules = ['space-assignment']

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                    sourceCompatibility JavaVersion.VERSION_1_8 // wrong level, should be in java block
                }
            }

            tasks.withType(JavaCompile) {
                description "Compiles the Java source"
                options.forkOptions { 
                    tempDir "temp"
                }
                doLast {
                    repositories {
                        maven {
                            url "https://example3.com"
                        }
                    }
                }
            }
            tasks.configureEach { // an action on DomainObjectCollection
                group "pew pew"
            }
            tasks.withType(JavaCompile).configureEach {
                options.forkOptions { 
                    tempDir "wololo" // not supported since it's hard
                }
            }
            task(t3) {
                description "t3"
            }
            task t6 {
                description "t6"
            }
            task (t7, type: Wrapper){
                distributionPath "t7"
            }
            task t9(type: Wrapper){ // unsupported since it's a nested call (with "t9" as method name) and it's harder to detect it reliably
                distributionPath "t9"
            }
            task ("t300", type: Wrapper){
                distributionPath "t300"
            }
            tasks.create([name: 't15']) {
                description "t15"
            }
            tasks.create('t18', Wrapper) {
               distributionPath "t18"
            }
            tasks.register('t22', Wrapper) {
               distributionPath "t22"
            }
        """

        when:
        def result = runTasks('autoLintGradle', '--warning-mode', 'none')

        then:
        result.output.contains("12 problems (0 errors, 12 warnings)")

        when:
        runTasks('fixLintGradle', '--warning-mode', 'none')

        then:
        buildFile.text.contains('description = "Compiles the Java source"')
        buildFile.text.contains('tempDir = "temp"')
        buildFile.text.contains('url = "https://example3.com"')
        buildFile.text.contains('sourceCompatibility = JavaVersion.VERSION_1_8')
        buildFile.text.contains('group = "pew pew"')
        //buildFile.text.contains('tempDir = "wololo"') //not supported
        buildFile.text.contains('description = "t3"')
        buildFile.text.contains('description = "t6"')
        buildFile.text.contains('distributionPath = "t7"')
        // buildFile.text.contains('distributionPath = "t9"')  //not supported
        buildFile.text.contains('distributionPath = "t300"')
        buildFile.text.contains('description = "t15"')
        buildFile.text.contains('distributionPath = "t18"')
        buildFile.text.contains('distributionPath = "t22"')

        and:
        runTasks('help', '--warning-mode', 'none')
    }
}
