package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.TestKitSpecification

class Issue39Spec extends TestKitSpecification {
    def 'place dependencies in the correct configuration by source set'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'nebula.integtest' version '3.2.1'
                id 'java'
            }

            gradleLint {
                rules = ['unused-dependency']
            }

            repositories { mavenCentral() }

            dependencies {
                // guava is a transitive dependency
                compile 'com.netflix.servo:servo-atlas:0.12.7'
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
        
        println(buildFile.text)
    }
}
