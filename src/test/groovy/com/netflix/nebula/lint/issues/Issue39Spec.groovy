package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.TestKitSpecification

class Issue39Spec extends TestKitSpecification {
    def 'place dependencies in the correct configuration by source set'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'nebula.integtest' version '5.1.2'
                id 'java'
            }

            gradleLint {
                rules = ['unused-dependency']
            }

            repositories { mavenCentral() }

            dependencies {
                compile group: 'com.google.guava', name: 'guava', version: '26.0-jre'
            }
        """

        createJavaFile(projectDir, '''
            import com.google.common.collect.*;
            public class A {
                Object m = HashMultimap.create();
            }
        ''', 'src/integTest/java')
                
        then:
        runTasksSuccessfully('compileIntegTestJava', 'fixGradleLint')
    }
}
