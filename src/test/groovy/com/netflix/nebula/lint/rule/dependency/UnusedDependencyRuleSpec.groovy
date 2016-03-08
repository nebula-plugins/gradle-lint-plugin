package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.plugin.GradleLintPlugin
import nebula.test.IntegrationSpec
import org.gradle.api.logging.LogLevel
import spock.lang.Unroll

class UnusedDependencyRuleSpec extends IntegrationSpec {
    LogLevel logLevel = LogLevel.DEBUG

    static def guava = 'com.google.guava:guava:18.0'
    static def asm = 'org.ow2.asm:asm:5.0.4'

    def main = '''
            import com.google.common.collect.*;
            public class Main {
                public static void main(String[] args) {
                    Multimap m = HashMultimap.create();
                }
            }
        '''

    // TODO source is matched up to the appropriate configuration --> junit moved from compile to testCompile if appropriate

    // TODO match up dependencies with source sets

    // TODO move dependencies from root project to subproject where appropriate

    // TODO look in web.xml for things like <filter-class>com.google.inject.servlet.GuiceFilter</filter-class>

    // TODO sort first order dependencies to be added

    // TODO should we be on the lookout for common runtime dependencies like xerces generally marked as compile?

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
        [guava, asm]                                          | [guava]
        ['io.springfox:springfox-core:2.0.2']                 | [guava]
        [guava, 'io.springfox:springfox-core:2.0.2']          | [guava]
    }

    @Unroll
    def 'runtime dependencies with \'#conf\' that are used at compile time are transformed into compile dependencies'() {
        setup:

        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'war'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                $conf 'com.google.guava:guava:18.0'
            }
        """

        createJavaSourceFile(projectDir, main)

        when:
        def result = runTasks('compileJava', 'fixGradleLint')
        println result.standardError
        println result.standardOutput

        then:
        dependencies(buildFile) == [guava]

        where:
        conf << ['runtime', 'providedRuntime']
    }

    @Unroll
    def 'runtime dependencies with configuration \'#conf\' that are unused at compile time are left alone'() {
        setup:

        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'war'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                $conf 'com.google.guava:guava:18.0'
            }
        """

        when:
        runTasks('compileJava', 'fixGradleLint')

        then:
        dependencies(buildFile, conf) == [guava]

        where:
        conf << ['runtime', 'providedRuntime']
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
        runTasks('compileTestJava', 'fixGradleLint')

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
                compile '$asm'
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
        runTasks('compileJava', 'fixGradleLint')

        then:
        dependencies(aBuildFile) == [guava]
    }

    @Unroll
    def 'provided dependencies with conf \'#conf\' are removed if there is no compile time reference'() {
        setup:

        buildFile.text = """
            plugins {
              id 'java'
              id $plugin
            }
            apply plugin: ${GradleLintPlugin.name}

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                $conf '$guava'
                $conf '$asm'
            }
        """

        createJavaSourceFile(projectDir, main)

        when:
        runTasks('compileJava', 'fixGradleLint')

        then:
        // also, provided dependencies are NOT moved to compile
        dependencies(buildFile, conf) == [guava]

        where:
        conf                | plugin
        'provided'          | "'nebula.provided-base' version '3.0.3'"
        'providedCompile'   | "'war'"
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
