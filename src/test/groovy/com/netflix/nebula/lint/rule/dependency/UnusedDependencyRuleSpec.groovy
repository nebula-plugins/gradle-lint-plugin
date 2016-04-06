/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    // TODO move dependencies from root project to subproject where appropriate

    // TODO look in web.xml for things like <filter-class>com.google.inject.servlet.GuiceFilter</filter-class>

    // TODO sort first order dependencies to be added

    // TODO should we be on the lookout for common runtime dependencies like xerces generally marked as compile?

    // TODO match indentation when adding dependencies

    // TODO move dependencies from runtime to compile if the dependency is required at compile time (possible if
    // the dependency is also being sourced transitively from a compile time dependency)

    @Unroll
    def 'unused compile dependencies are marked for deletion'() {
        when:
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

        then:
        def results = runTasksSuccessfully('compileJava', 'fixGradleLint')
        println results.standardOutput

        dependencies(buildFile) == expected

        where:
        deps                                                  | expected
//        [guava, asm]                                          | [guava]
        ['io.springfox:springfox-core:2.0.2']                 | [guava]
//        [guava, 'io.springfox:springfox-core:2.0.2']          | [guava]
    }

    @Unroll
    def 'runtime dependencies with \'#conf\' that are used at compile time are transformed into compile dependencies'() {
        when:
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

        then:
        runTasksSuccessfully('compileJava', 'fixGradleLint')
        dependencies(buildFile) == [guava]

        where:
        conf << ['runtime', 'providedRuntime']
    }

    @Unroll
    def 'runtime dependencies with configuration \'#conf\' that are unused at compile time are left alone'() {
        when:
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'war'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                $conf 'com.google.guava:guava:18.0'
            }
        """

        then:
        runTasksSuccessfully('compileJava', 'fixGradleLint')
        dependencies(buildFile, conf) == [guava]

        where:
        conf << ['runtime', 'providedRuntime']
    }

    def 'find dependency references in test code'() {
        when:
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

        then:
        runTasksSuccessfully('compileTestJava', 'fixGradleLint')
        dependencies(buildFile) == ['junit:junit:4.+']
    }

    def 'unused dependency in a subproject is marked for deletion'() {
        when:
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

        then:
        runTasksSuccessfully('compileJava', 'fixGradleLint')
        dependencies(aBuildFile) == [guava]
    }

    @Unroll
    def 'providedCompile dependencies are removed if there is no compile time reference'() {
        when:
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'war'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                providedCompile '$guava'
                providedCompile '$asm'
            }
        """

        createJavaSourceFile(projectDir, main)

        then:
        runTasksSuccessfully('compileJava', 'fixGradleLint')

        // also, provided dependencies are NOT moved to compile
        dependencies(buildFile, 'providedCompile') == [guava]
    }

    def 'dependencies that are indirectly required through the type hierarchy are not removed'() {
        when:
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'war'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                compile 'com.google.inject.extensions:guice-servlet:3.0'
                compile 'javax.servlet:servlet-api:2.5'
            }
        """

        createJavaSourceFile(projectDir, '''
            public abstract class Main extends com.google.inject.servlet.GuiceServletContextListener {
            }
        ''')

        then:
        runTasksSuccessfully('compileJava', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.inject.extensions:guice-servlet:3.0', 'javax.servlet:servlet-api:2.5']
    }

    def 'dependencies are moved to a configuration that matches the source set(s) that refer to them'() {
        when:
        buildFile.text = """
            apply plugin: ${GradleLintPlugin.name}
            apply plugin: 'java'

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                compile 'junit:junit:4.12'
            }
        """

        createJavaTestFile(projectDir, '''
            import org.junit.Test;
            public class Test1 {
                @Test
                public void test() {}
            }
        ''')

        then:
        runTasksSuccessfully('compileTestJava', 'fixGradleLint')

        dependencies(buildFile, 'compile') == []
        dependencies(buildFile, 'testCompile') == ['junit:junit:4.12']
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
