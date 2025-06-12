package com.netflix.nebula.lint.rule.dsl

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import spock.lang.Subject

@Subject(SpaceAssignmentRule)
class SpaceAssignmentRuleSpec extends BaseIntegrationTestKitSpec {

    def setup() {
        System.setProperty("ignoreDeprecations", "true")
    }

    def cleanup() {
        System.setProperty("ignoreDeprecations", "false")
    }

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

    def 'reports and fixes a violation if space assignment syntax is used as a property getter - simple cases'() {
        buildFile << """
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            gradleLint.rules = ['space-assignment']
            tasks.register('task1') {
                if (project.hasProperty('myCustomDescription')) {
                    description = project.property('myCustomDescription')
                }
                doLast {
                    logger.warn "myCustomDescription: \${description}"
                }
            }
            tasks.register('task2', Exec) {
                commandLine "echo"
                args ('--task2', project.property("myCustomProperty1"))
                doLast {
                    logger.warn "myCustomProperty1: \${project.property('myCustomProperty1')}"
                }
            }
            def a = project.property "myCustomProperty2"
            
            def subA = project(':subA')
            subA.afterEvaluate { evaluatedProject ->
                def b = project.rootProject.allprojects.find { it.name == "subA"}.property("myCustomPropertySubA1")
                def c = rootProject.childProjects.subA.property("myCustomPropertySubA2")
                def d = project.findProject(':subA').property("myCustomPropertySubA3")
                tasks.register('showProperties') {
                    doLast {
                        logger.warn "myCustomProperty2: \${a}"
                        logger.warn "subA.myCustomPropertySubA1: \${b}"
                        logger.warn "subA.myCustomPropertySubA2: \${c}"
                        logger.warn "subA.myCustomPropertySubA3: \${d}"
                    }
                }
            }
            """.stripIndent()

        new File(projectDir, "gradle.properties") << """)
            myCustomDescription=This is a custom description
            myCustomProperty1=property1
            myCustomProperty2=property2
        """.stripIndent()

        addSubproject('subA', """
            ext {
                myCustomPropertySubA1 = "subA-one"
                myCustomPropertySubA2 = "subA-two"
                myCustomPropertySubA3 = "subA-three"
            }
            """.stripIndent())

        when:
        runTasks('fixLintGradle', '--warning-mode', 'none')
        def results = runTasks('task1', 'task2', 'showProperties')

        then: "fixes are applied correctly"
        buildFile.text.contains("description = project.findProperty('myCustomDescription')")
        buildFile.text.contains("args ('--task2', project.findProperty(\"myCustomProperty1\"))")
        buildFile.text.contains("logger.warn \"myCustomProperty1: \${project.findProperty('myCustomProperty1')}\"")
        buildFile.text.contains("def a = project.findProperty \"myCustomProperty2\"")
        buildFile.text.contains("def b = project.rootProject.allprojects.find { it.name == \"subA\"}.findProperty(\"myCustomPropertySubA1\")")
//        buildFile.text.contains("def c = rootProject.childProjects.subA.findProperty(\"myCustomPropertySubA2\")") # TODO: investigate why this is intermittently not working
        buildFile.text.contains("def d = project.findProject(':subA').findProperty(\"myCustomPropertySubA3\")")

        and: "properties are read and printed correctly"
        results.output.contains("myCustomDescription: This is a custom description")
        results.output.contains("myCustomProperty1: property1")
        results.output.contains("myCustomProperty2: property2")
        results.output.contains("subA.myCustomPropertySubA1: subA-one")
        results.output.contains("subA.myCustomPropertySubA2: subA-two")
        results.output.contains("subA.myCustomPropertySubA3: subA-three")
    }
}
