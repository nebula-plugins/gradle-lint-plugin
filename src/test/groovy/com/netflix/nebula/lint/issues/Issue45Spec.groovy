package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Issue

class Issue45Spec extends TestKitSpecification {
    
    @Issue('45')
    def 'interaction with nebula.dependency-recommender'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'nebula.dependency-recommender' version '3.6.3'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency']

            dependencyRecommendations {
                propertiesFile file: file('dependency-versions.properties')
            }

            repositories { mavenCentral() }

            dependencies {
                testCompile 'junit:junit'
            }
        """

        new File(projectDir, 'dependency-versions.properties') << 'junit:junit = 4.11'
        
        createJavaTestFile('public class A {}')

        then:
        def results = runTasksSuccessfully('compileTestJava')
        println(results.output)
        results.output.contains('unused-dependency')
    }
}
