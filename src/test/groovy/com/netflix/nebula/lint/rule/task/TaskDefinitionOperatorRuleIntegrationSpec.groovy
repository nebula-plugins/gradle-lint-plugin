package com.netflix.nebula.lint.rule.task

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Subject

@Subject(TaskDefinitionOperatorRule)
class TaskDefinitionOperatorRuleIntegrationSpec extends TestKitSpecification {

    def setup() {
        gradleVersion = "4.10.2"
    }

    def 'replace tasks with << with proper doLast'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask << {
                println 'hello'
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

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask {
                doLast {
                    println 'hello'
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - with inner closure one line '() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task showMeCache << {
                configurations.compile.each { println it }
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

            gradleLint.rules = ['deprecated-task-operator']

            task showMeCache {
                doLast {
                    configurations.compile.each { println it }
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - with inner closure multi line '() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task showMeCache << {
                configurations.compile.each { 
                    println it 
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

            gradleLint.rules = ['deprecated-task-operator']

            task showMeCache {
                doLast {
                    configurations.compile.each { 
                        println it 
                    }
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - one line'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask << { println 'hello' }       
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

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask {
                doLast {
                    println 'hello' 
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - one line - multi statement'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask << { println 'hello'; println "world" }       
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

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask {
                doLast {
                    println 'hello'; println "world" 
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - multi statements'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask << {
                println 'hello'
                println 'world'
                println '!!!'
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

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask {
                doLast {
                    println 'hello'
                    println 'world'
                    println '!!!'
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - multiple tasks'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask << {
                println 'hello'
            }
            
            task helloTask2 << {
                println 'world'
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

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask {
                doLast {
                    println 'hello'
                }
            }
            
            task helloTask2 {
                doLast {
                    println 'world'
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - long definition'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task runReport << {
                def props = [foo: 'bar'] as Properties
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

            gradleLint.rules = ['deprecated-task-operator']

            task runReport {
                doLast {
                    def props = [foo: 'bar'] as Properties
                }
            }  
        """.trim()
    }

    def 'replace tasks with << with proper doLast - different notation'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask << 
            {
                println 'hello'
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

            gradleLint.rules = ['deprecated-task-operator']

            task helloTask {
                doLast {
                    println 'hello'
                }
            }  
        """.trim()
    }

    def 'no autofix when task setup is more complex'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
                id 'org.springframework.boot' version '2.2.4.RELEASE'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task setDevProperties(dependsOn: bootRun) << {
                doFirst {
                    jvmArg '-Dlog4j.logger.level=DEBUG'
            }
        }  
        """

        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        result.output.contains("needs fixing   deprecated-task-operator           The << operator was deprecated. Need to use doLast method")
    }

}
