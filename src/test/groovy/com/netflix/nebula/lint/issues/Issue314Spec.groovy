package com.netflix.nebula.lint.issues

import nebula.test.IntegrationTestKitSpec

class Issue314Spec extends IntegrationTestKitSpec {

    def main = '''
            import org.apache.commons.logging.Log;
            import org.apache.commons.logging.LogFactory;
            public class Main {
                public static void main(String[] args) {
                    Log log = LogFactory.getLog(Main.class);
                    log.info("foo");
                }
            }
        '''

    def 'lintGradle fails when build.gradle has java-test-fixtures'() {
        setup:
        writeJavaSourceFile(main, 'src/testFixtures/java')

        buildFile.text = """
            plugins {
                id "java-library"
                id "java-test-fixtures"
                id "nebula.lint"
            }

            gradleLint {
                rules = ['all-dependency']
            }

            repositories { mavenCentral() }

            dependencies {
                testFixturesImplementation "commons-logging:commons-logging:1.2"
            }
        """

        when:
        def results = runTasks('lintGradle')

        then:
        println results?.output
        noExceptionThrown()
    }
}