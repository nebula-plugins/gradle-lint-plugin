package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.plugin.GradleLintPlugin
import nebula.test.IntegrationSpec
import org.gradle.api.logging.LogLevel
import spock.lang.Unroll

class UnusedDependencyRuleSpec extends IntegrationSpec {
    LogLevel logLevel = LogLevel.DEBUG

    static def guava = 'com.google.guava:guava:18.0'

    def main = '''
            import com.google.common.collect.*;
            public class Main {
                public static void main(String[] args) {
                    Multimap m = HashMultimap.create();
                }
            }
        '''

    // TODO provided dependencies are not moved to runtime

    // TODO provided dependencies are removed if there is no compile time reference

    // TODO source is matched up to the appropriate configuration --> junit moved from compile to testCompile if appropriate

    // TODO match up dependencies with source sets

    @Unroll
    def 'unused compile dependencies are marked for deletion'() {
        setup:

        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                ${deps.collect { "compile '$it'" }.join('\n') }
            }
        """

        createJavaSourceFile(projectDir, main)

        when:
        runTasks('compileJava', 'fixGradleLint')

        then:
        dependencies(buildFile) == expected

        where:
        deps                                                  | expected
        [guava, 'org.ow2.asm:asm:5.0.4']                      | [guava]
        ['io.springfox:springfox-core:2.0.2']                 | [guava]
        [guava, 'io.springfox:springfox-core:2.0.2']          | [guava]
    }

    def 'runtime dependencies that are used at compile time are transformed into compile dependencies'() {
        setup:

        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                runtime 'com.google.guava:guava:18.0'
            }
        """

        createJavaSourceFile(projectDir, main)

        when:
        runTasks('compileJava', 'fixGradleLint')

        then:
        dependencies(buildFile) == [guava]
    }

    def 'runtime dependencies that are unused at compile time are left alone'() {
        setup:

        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                runtime 'com.google.guava:guava:18.0'
            }
        """

        when:
        runTasks('compileJava', 'fixGradleLint')

        println(buildFile.text)

        then:
        dependencies(buildFile, 'runtime') == [guava]
    }

    def 'find dependency references in test code'() {
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                testCompile 'junit:junit:4.+'
            }
        """

        createJavaTestFile(projectDir, '''
            import static org.junit.Assert.*;
            import org.junit.Test;

            public class MainTest {
                @Test
                public void performTest() {
                    assertEquals(1, 1);
                }
            }
        ''')

        when:
        def result = runTasks('compileTestJava', 'fixGradleLint')
        println(result.standardOutput)

        then:
        dependencies(buildFile) == ['junit:junit:4.+']
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
                compile '$guava'
                compile 'org.ow2.asm:asm:5.0.4'
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
        dependencies(aBuildFile) == [guava]
    }

    def dependencies(File _buildFile, String... confs = ['compile', 'testCompile']) {
        _buildFile.text.readLines()
                .collect { it.trim() }
                .findAll { line -> confs.any { c -> line.startsWith(c) } }
                .collect { it.split(/\s+/)[1].replaceAll(/'/, '') }
                .sort()
    }

    def createJavaSourceFile(File projectDir, String source) {
        createJavaFile(projectDir, source, 'src/main/java')
    }

    def createJavaTestFile(File projectDir, String source) {
        createJavaFile(projectDir, source, 'src/test/java')
    }

    def createJavaFile(File projectDir, String source, String sourceFolderPath) {
        def sourceFolder = new File(projectDir, sourceFolderPath)
        sourceFolder.mkdirs()
        new File(sourceFolder, JavaFixture.fullyQualifiedName(source).replaceAll(/\./, '/') + '.java').text = source
    }
}
