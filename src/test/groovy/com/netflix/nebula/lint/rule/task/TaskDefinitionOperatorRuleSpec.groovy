package com.netflix.nebula.lint.rule.task

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec
import spock.lang.Subject

@Subject(TaskDefinitionOperatorRule)
class TaskDefinitionOperatorRuleSpec extends AbstractRuleSpec {

    def 'report a violation if << is used to declare task'() {
        project.buildFile << """
            apply plugin: 'java'

            task helloTask << {
                println 'hello'
            }            
        """

        when:
        def response = runRulesAgainst(new TaskDefinitionOperatorRule())

        then:
        response.violations.size() == 1
        response.violations[0].message == 'The << operator was deprecated. Need to use doLast method'
    }

    def 'reports multiple violations if << is used to declare tasks'() {
        project.buildFile << """
            apply plugin: 'java'

            task helloTask << {
                println 'hello'
            } 
            
            task helloTask2 << {
                println 'hello'
            }            
        """

        when:
        def response = runRulesAgainst(new TaskDefinitionOperatorRule())

        then:
        response.violations.size() == 2
        response.violations[0].message == 'The << operator was deprecated. Need to use doLast method'
        response.violations[1].message == 'The << operator was deprecated. Need to use doLast method'
    }

    def 'do not report a violation if << is not used to declare task'() {
        project.buildFile << """
            apply plugin: 'java'

            task helloTask { 
                doLast {
                    println 'hello'
                }
            }            
        """

        when:
        def response = runRulesAgainst(new TaskDefinitionOperatorRule())

        then:
        response.violations.size() == 0
    }

}
