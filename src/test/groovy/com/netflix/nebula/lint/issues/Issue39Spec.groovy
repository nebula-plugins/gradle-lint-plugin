package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import com.netflix.nebula.lint.rule.dependency.UnusedDependencyRule
import spock.lang.Subject

@Subject(UnusedDependencyRule)
class Issue39Spec extends BaseIntegrationTestKitSpec {
    def 'place dependencies in the correct configuration by source set'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'com.netflix.nebula.integtest' version '10.1.4'
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
