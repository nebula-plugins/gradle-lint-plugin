package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.plugin.GradleLintPlugin
import nebula.test.IntegrationSpec
import org.gradle.api.logging.LogLevel

class UnusedDependencyRuleSpec extends IntegrationSpec {
    LogLevel logLevel = LogLevel.DEBUG

    def 'unused dependency is marked for deletion'() {
        setup:
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                compile 'com.google.guava:guava:19.0'
                compile 'commons-configuration:commons-configuration:latest.release'
            }
        """

        createJavaSourceFile(projectDir, '''
            import com.google.common.collect.*;
            public class Main {
                public static void main(String[] args) {
                    Multimap m = HashMultimap.create();
                }
            }
        ''')

        when:
        def result = runTasks('compileJava', 'fixGradleLint')

        then:
        dependencies(buildFile) == ['com.google.guava:guava:19.0']
    }

    def 'unused dependency in a subproject is marked for deletion'() {
        setup:
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            gradleLint.rules = ['unused-dependency']
        """

        def aDir = addSubproject('a')
        def aBuildFile = new File(aDir, 'build.gradle')

        aBuildFile << """
            apply plugin: 'java'

            repositories { mavenCentral() }

            dependencies {
                compile 'com.google.guava:guava:19.0'
                compile 'commons-configuration:commons-configuration:latest.release'
            }
        """

        createJavaSourceFile(aDir, '''
            import com.google.common.collect.*;
            public class Main {
                public static void main(String[] args) {
                    Multimap m = HashMultimap.create();
                }
            }
        ''')

        when:
        def result = runTasks('compileJava', 'fixGradleLint')
        println(result.standardOutput)

        then:
        dependencies(aBuildFile) == ['com.google.guava:guava:19.0']
    }

    def dependencies(File _buildFile) {
        _buildFile.text.readLines()
                .collect { it.trim() }
                .findAll { it.startsWith('compile') || it.startsWith('testCompile') }
                .collect { it.split(/\s+/)[1].replaceAll(/'/, '') }
                .sort()
    }

    def createJavaSourceFile(File projectDir, String source) {
        def sourceFolder = new File(projectDir, 'src/main/java')
        sourceFolder.mkdirs()
        new File(sourceFolder, JavaFixture.fullyQualifiedName(source).replaceAll(/\./, '/') + '.java').text = source
    }
}
