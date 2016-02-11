package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.plugin.GradleLintPlugin
import nebula.test.IntegrationSpec

class UnusedDependencyRuleSpec extends IntegrationSpec {
    File mainClass

    def setup() {
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories {
                mavenCentral()
            }
        """

        def sourceFolder = new File(projectDir, 'src/main/java')
        sourceFolder.mkdirs()

        mainClass = new File(sourceFolder, 'Main.java')
    }

    def cleanup() {
        mainClass.delete()
    }

    def 'unused dependency is marked for deletion'() {
        setup:
        buildFile << """
            dependencies {
                compile 'com.google.guava:guava:19.0'
                compile 'commons-configuration:commons-configuration:latest.release'
            }
        """

        mainClass << '''
            import com.google.common.collect.*;

            public class Main {
                Multimap f1 = HashMultimap.create();

                public static void main(String[] args) {
                    Multimap m = HashMultimap.create();
                }
            }
        '''

        when:
        runTasks('compileJava', 'fixGradleLint')

        then:
        dependencies() == ['com.google.guava:guava:19.0']
    }

    def dependencies() {
        buildFile.text.readLines()
                .collect { it.trim() }
                .findAll { it.startsWith('compile') || it.startsWith('testCompile') }
                .collect { it.split(/\s+/)[1].replaceAll(/'/, '') }
                .sort()
    }
}
