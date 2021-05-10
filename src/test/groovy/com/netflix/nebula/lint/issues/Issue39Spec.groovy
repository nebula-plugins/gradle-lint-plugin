package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.rule.dependency.UnusedDependencyRule
import nebula.test.IntegrationTestKitSpec
import spock.lang.Subject

@Subject(UnusedDependencyRule)
class Issue39Spec extends IntegrationTestKitSpec {
    def 'place dependencies in the correct configuration by source set'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'nebula.integtest' version '7.0.7'
                id 'java'
            }

            gradleLint {
                rules = ['unused-dependency']
            }

            repositories { mavenCentral() }

            dependencies {
                implementation group: 'com.google.guava', name: 'guava', version: '26.0-jre'
            }
        """

        writeUnitTest('''
            import com.google.common.collect.*;
            public class A {
                Object m = HashMultimap.create();
            }
        ''', new File(projectDir, 'src/integTest/java'))

        then:
        runTasks('fixGradleLint')

        buildFile.text.contains("integTestImplementation 'com.google.guava:guava:26.0-jre'")
    }
}
