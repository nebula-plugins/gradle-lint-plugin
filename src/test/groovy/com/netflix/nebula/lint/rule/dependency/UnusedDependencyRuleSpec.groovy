package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.plugin.GradleLintPlugin
import nebula.test.IntegrationSpec

class UnusedDependencyRuleSpec extends IntegrationSpec {
    def 'unused dependency is marked for deletion'() {
        setup:
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'com.google.guava:guava:19.0'
                compile 'commons-configuration:commons-configuration:latest.release'
            }
        """

        def sourceFolder = new File(projectDir, 'src/main/java')
        sourceFolder.mkdirs()

        def main = new File(sourceFolder, 'Main.java')
        main.text = '''
            import com.google.common.collect.*;

            public class Main {
                Multimap f1 = HashMultimap.create();

                public static void main(String[] args) {
                    Multimap m = HashMultimap.create();
                }
            }
        '''

        when:
        def results = runTasks('compileJava', 'fixGradleLint')
        println(results.standardOutput)
        println(results.standardError)

        then:
        buildFile.text == """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories {
                mavenCentral()
            }

            dependencies {
                compile 'com.google.guava:guava:19.0'
            }
        """.toString()
    }
}
