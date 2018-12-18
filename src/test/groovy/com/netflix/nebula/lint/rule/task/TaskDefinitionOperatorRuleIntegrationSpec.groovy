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
                def props = [user: 'vmsuser', password: 'vmspassword', allowMultiQueries: 'true'] as Properties
                def url = 'jdbc:mysql://vmsperfdata.ceqg1dgfu0mp.us-east-1.rds.amazonaws.com:3306/vmsperfdata'
                def driver = 'com.mysql.jdbc.Driver'
                def sql = Sql.newInstance(url, props, driver)
                
                sql.eachRow("SELECT * FROM vmsperfdata.GcLowWaterMark") {
                    println "Gromit likes \${it.Global}"
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

            task runReport {
                doLast {
                    def props = [user: 'vmsuser', password: 'vmspassword', allowMultiQueries: 'true'] as Properties
                    def url = 'jdbc:mysql://vmsperfdata.ceqg1dgfu0mp.us-east-1.rds.amazonaws.com:3306/vmsperfdata'
                    def driver = 'com.mysql.jdbc.Driver'
                    def sql = Sql.newInstance(url, props, driver)
                    sql.eachRow("SELECT * FROM vmsperfdata.GcLowWaterMark") {
                        println "Gromit likes \${it.Global}"
                    }
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

    def 'Build fail  with edge case scenarios'() {
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['deprecated-task-operator']

            task setDevProperties(dependsOn: bootRun) << {
                doFirst {
                    jvmArg '-DNETFLIX_STACK=testintg'
                    jvmArg '-DBH_OVERRIDE_STACK=avasani'
                    jvmArg '-Dnetflix.environment=test'
                    jvmArg '-Dnetflix.appinfo.metadata.enableRoute53=false'
                    jvmArg '-Dnetflix.appinfo.region=us-east-1'
                    jvmArg '-Dnetflix.discovery.registration.enabled=true'
                    jvmArg '-Dnetflix.appinfo.validateInstanceId=false'
                    jvmArg '-Dnetflix.appinfo.doNotInitWithAmazonInfo=true'
                    jvmArg '-Dcom.netflix.asterix.buzzerbee.disabled=true'
                    jvmArg '-Dlog4j.logger.com.netflix.conductor.client.task.WorkflowTaskCoordinator=DEBUG'
            }
        }  
        """

        when:
        def result = runTasksFail('fixGradleLint', '--warning-mode=none')

        then:
        result.output.contains("warning   deprecated-task-operator           The << operator was deprecated. Need to use doLast method (no auto-fix available)")
    }

}
