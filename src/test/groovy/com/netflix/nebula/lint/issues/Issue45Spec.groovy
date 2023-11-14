package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import com.netflix.nebula.lint.rule.dependency.UnusedDependencyRule
import spock.lang.Issue
import spock.lang.Subject

@Subject(UnusedDependencyRule)
class Issue45Spec extends BaseIntegrationTestKitSpec {
    @Issue('45')
    def 'interaction with nebula.dependency-recommender'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'nebula.dependency-recommender' version '9.0.2'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency']

            dependencyRecommendations {
                propertiesFile file: file('dependency-versions.properties')
            }

            repositories { mavenCentral() }

            dependencies {
                testImplementation 'junit:junit'
            }
        """

        new File(projectDir, 'dependency-versions.properties') << 'junit:junit = 4.11'

        writeUnitTest('public class A {}') // notice how the dependency is not in here

        then:
        def results = runTasks('compileTestJava')
        results.output.contains('unused-dependency')
    }
}
