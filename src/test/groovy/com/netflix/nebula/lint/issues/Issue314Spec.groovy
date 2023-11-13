package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec

class Issue314Spec extends BaseIntegrationTestKitSpec {

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

    def 'lintGradle does not fail when build.gradle has java-test-fixtures'() {
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

    def 'lintGradle finds unused dependencies in java-test-fixtures'() {
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
                testFixturesImplementation "commons-io:commons-io:2.8.0"
                testFixturesImplementation "commons-logging:commons-logging:1.2"
            }
        """

        when:
        def results = runTasksAndFail('lintGradle')

        then:
        println results?.output
        results.output.contains('this dependency is unused and can be removed')
    }

    def 'fixGradleLint should remove unused dependencies in java-test-fixtures'() {
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
                testFixturesImplementation "commons-io:commons-io:2.8.0"
                testFixturesImplementation "commons-logging:commons-logging:1.2"
            }
        """

        when:
        def results = runTasks('fixGradleLint')

        then:
        println results?.output
        results.output.contains('this dependency is unused and can be removed')
        results.output.contains('build.gradle:15')
        results.output.contains('testFixturesImplementation "commons-io:commons-io:2.8.0"')
    }
}