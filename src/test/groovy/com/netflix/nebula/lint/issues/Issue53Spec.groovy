package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Issue

class Issue53Spec extends TestKitSpecification {
    
    @Issue('53')
    def 'only one violation reported when a dependency is unused by multiple configurations'() {
        when:
        buildFile << '''
            plugins {
                id 'nebula.lint'
                id 'java'
                id 'nebula.integtest' version '3.3.0'
            }
            
            repositories { mavenCentral() }
            
            dependencies {
                testCompile 'junit:junit:4.11'
            }
            
            gradleLint.rules = ['unused-dependency']
        '''
        
        createJavaTestFile('public class A {}')
        
        then:
        def results = runTasksSuccessfully('compileTestJava', 'lintGradle')
        println(results.output)
        results.output.readLines().count { it.contains('unused-dependency') } == 1
    }
}
