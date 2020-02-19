/*
 * Copyright 2015-2019 Netflix, Inc.
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

import com.netflix.nebula.lint.TestKitSpecification
import spock.lang.Issue
import spock.lang.Unroll

class UnusedDependencyRuleSpec extends TestKitSpecification {
    static def guava = 'com.google.guava:guava:18.0'
    static def asm = 'org.ow2.asm:asm:5.0.4'
    // Dependency that provides guava:18.0 transitively
    static def springfox = 'io.springfox:springfox-core:2.0.2'

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

    // TODO match indentation when adding dependencies

    @Unroll
    def 'unused compile dependencies are marked for deletion'() {
        when:
        buildFile.text = """\
            |plugins {
            |    id 'nebula.lint'
            |    id 'java'
            |}
            |
            |gradleLint.rules = ['unused-dependency']
            |
            |repositories { mavenCentral() }
            |
            |dependencies {
            ${deps.collect { "|   compile '$it'" }.join('\n')}
            |}
            |""".stripMargin()

        createJavaSourceFile(main)

        then:
        def result = runTasksSuccessfully('compileJava', 'autoLintGradle')

        expectedWarnings.each {
            assert result.output.contains(it)
        }

        where:
        deps               | expectedWarnings
        [guava, asm]       | ['warning   unused-dependency                  this dependency is unused and can be removed']
        [springfox]        | ['warning   unused-dependency                  one or more classes in com.google.guava:guava:18.0 are required by your code directly', 'warning   unused-dependency                  this dependency is unused and can be removed']
        [guava, springfox] | ['warning   unused-dependency                  this dependency is unused and can be removed']
    }


    @Unroll
    def 'unused api dependencies are marked for deletion'() {
        when:
        buildFile.text = """\
            |plugins {
            |    id 'nebula.lint'
            |    id 'java-library'
            |}
            |
            |gradleLint.rules = ['unused-dependency']
            |
            |repositories { mavenCentral() }
            |
            |dependencies {
            ${deps.collect { "|   api '$it'" }.join('\n')}
            |}
            |""".stripMargin()

        createJavaSourceFile(main)

        then:
        def result = runTasksSuccessfully('compileJava', 'autoLintGradle')

        expectedWarnings.each {
            assert result.output.contains(it)
        }

        where:
        deps               | expectedWarnings
        [guava, asm]       | ['warning   unused-dependency                  this dependency is unused and can be removed']
        [springfox]        | ['warning   unused-dependency                  one or more classes in com.google.guava:guava:18.0 are required by your code directly', 'warning   unused-dependency                  this dependency is unused and can be removed']
        [guava, springfox] | ['warning   unused-dependency                  this dependency is unused and can be removed']
    }

    @Unroll
    def 'unused implementation dependencies are marked for deletion'() {
        when:
        buildFile.text = """\
            |plugins {
            |    id 'nebula.lint'
            |    id 'java-library'
            |}
            |
            |gradleLint.rules = ['unused-dependency']
            |
            |repositories { mavenCentral() }
            |
            |dependencies {
            ${deps.collect { "|   implementation '$it'" }.join('\n')}
            |}
            |""".stripMargin()

        createJavaSourceFile(main)

        then:
        def result = runTasksSuccessfully('compileJava', 'autoLintGradle')

        expectedWarnings.each {
            assert result.output.contains(it)
        }

        where:
        deps               | expectedWarnings
        [guava, asm]       | ['warning   unused-dependency                  this dependency is unused and can be removed']
        [springfox]        | ['warning   unused-dependency                  one or more classes in com.google.guava:guava:18.0 are required by your code directly', 'warning   unused-dependency                  this dependency is unused and can be removed']
        [guava, springfox] | ['warning   unused-dependency                  this dependency is unused and can be removed']
    }

    def 'find dependency references in test code'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

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
            plugins {
                id 'nebula.lint'
            }

            gradleLint.rules = ['unused-dependency']
        """

        def aDir = new File(projectDir, 'a')
        aDir.mkdirs()
        new File(projectDir, 'settings.gradle').text = /include 'a'/
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
        runTasksSuccessfully('a:compileJava', 'fixGradleLint')
        dependencies(aBuildFile) == [guava]
    }

    def 'providedCompile dependencies are removed if there is no compile time reference'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'war'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                providedCompile '$guava'
                providedCompile '$asm'
            }
        """

        createJavaSourceFile(main)

        then:
        def x = runTasksSuccessfully('assemble', 'fixGradleLint')

        // also, provided dependencies are NOT moved to compile
        dependencies(buildFile, 'providedCompile') == [guava]
    }

    def 'dependencies that are indirectly required through the type hierarchy are not removed'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'war'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                compile 'com.google.inject.extensions:guice-servlet:3.0'
                compile 'javax.servlet:servlet-api:2.5'
            }
        """

        createJavaSourceFile('''
            public abstract class Main extends com.google.inject.servlet.GuiceServletContextListener {
            }
        ''')

        then:
        runTasksSuccessfully('compileJava', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.inject.extensions:guice-servlet:3.0', 'javax.servlet:servlet-api:2.5']
    }

    def 'suggest that dependencies should be moved to a configuration that matches the source set(s) that refer to them'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                implementation 'junit:junit:4.12'
            }
        """

        createJavaSourceFile('public class Main {}')

        createJavaTestFile(projectDir, '''
            import org.junit.Test;
            public class Test1 {
                @Test
                public void test() {}
            }
        ''')

        then:
        def result = runTasksSuccessfully('compileTestJava', 'fixGradleLint')
        result.output.contains('fixed          unused-dependency                  this dependency should be moved to configuration testImplementation')
        !result.output.contains('unfixed        unused-dependency')
        !result.output.contains('this dependency is unused and can be removed')
    }

    def 'suggest that dependencies should be moved - used in only 1 nested configuration'() {
        buildFile.text = """
            buildscript {
                repositories { maven { url "https://plugins.gradle.org/m2/" } }
                dependencies {
                    classpath "com.netflix.nebula:nebula-project-plugin:7.0.7"
                }
            }
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            apply plugin: "nebula.integtest"
            gradleLint.rules = ['unused-dependency']
            repositories { mavenCentral() }
            dependencies {
                implementation 'junit:junit:4.12'
            }
        """

        createJavaSourceFile('public class Main {}')

        createJavaFile(projectDir, '''
            import org.junit.Test;
            public class Test1 {
                @Test
                public void test() {}
            }
        ''', 'src/integTest/java')

        when:
        def result = runTasksSuccessfully('compileTestJava', 'fixGradleLint')

        then:
        result.output.contains('fixed          unused-dependency                  this dependency should be moved to configuration integTestImplementation')
        !result.output.contains('unfixed        unused-dependency')
        !result.output.contains('this dependency is unused and can be removed')
    }

    def 'suggest that dependencies should be moved - used in 2 configurations in a hierarchy'() {
        buildFile.text = """
            buildscript {
                repositories { maven { url "https://plugins.gradle.org/m2/" } }
                dependencies {
                    classpath "com.netflix.nebula:nebula-project-plugin:7.0.7"
                }
            }
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            apply plugin: "nebula.integtest"
            gradleLint.rules = ['unused-dependency']
            repositories { mavenCentral() }
            dependencies {
                implementation 'junit:junit:4.12'
            }
        """

        createJavaSourceFile('public class Main {}')

        createJavaTestFile(projectDir, '''
            import org.junit.Test;
            public class Test1 {
                @Test
                public void test() {}
            }
        ''')

        createJavaFile(projectDir, '''
            import org.junit.Test;
            public class Test1 {
                @Test
                public void test() {}
            }
        ''', 'src/integTest/java')

        when:
        def result = runTasksSuccessfully('compileTestJava', 'fixGradleLint')

        then:
        result.output.contains('fixed          unused-dependency                  this dependency should be moved to configuration testImplementation')
        !result.output.contains('unfixed        unused-dependency')
        !result.output.contains('this dependency is unused and can be removed')
    }

    def 'webjars should be moved to runtime'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.webjars:acorn:0.5.0'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def result = runTasksSuccessfully('assemble', 'autoLintGradle')
        result.output.contains('warning   unused-dependency                  webjars should be in the runtimeOnly configuration (no auto-fix available)')
    }

    def 'dependencies present in more than one configuration as first order dependencies can be removed from one of them'() {
        given:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                implementation 'junit:junit:4.11'
                testImplementation 'junit:junit:4.11'
            }
        """

        createJavaSourceFile('public class Main {}')

        createJavaTestFile(projectDir, '''
            import org.junit.Test;
            public class Test1 {
                @Test
                public void test() {}
            }
        ''')

        when:
        runTasksSuccessfully('fixGradleLint')

        then:
        dependencies(buildFile, 'implementation') == []
        dependencies(buildFile, 'testImplementation') == ['junit:junit:4.11']
    }

    def 'service providers should be moved to the runtime configuration if their classes are unused at compile time'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                implementation 'mysql:mysql-connector-java:6.0.2'
            }
        """

        createJavaSourceFile('public class Main {}')

        then:
        def result = runTasksSuccessfully('compileJava', 'autoLintGradle')
        result.output.contains('warning   unused-dependency                  this dependency is a service provider unused at compileClasspath time and can be moved to the runtimeOnly configuration (no auto-fix available)')
        result.output.contains('warning   unused-dependency                  this dependency is unused and can be removed')
    }

    def 'remove \'family\' jars in favor of the components that make them up'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                implementation 'com.amazonaws:aws-java-sdk:1.10.76'
            }
        """

        createJavaSourceFile('''\
            import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
            public class Main {
                Object provider = new DefaultAWSCredentialsProviderChain();
            }''')

        then:
        def result = runTasksSuccessfully('compileJava', 'autoLintGradle')
        result.output.contains('warning   unused-dependency                  one or more classes in com.amazonaws:aws-java-sdk-core:1.10.76 are required by your code directly (no auto-fix available)')
        result.output.contains('warning   unused-dependency                  this dependency should be removed since its artifact is empty (no auto-fix available)')
    }

    def 'dependencies block in a root project which does not have the java plugin applied'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                gradleLint.rules = ['unused-dependency']
            }

            subprojects {
                apply plugin: 'java'
                repositories { mavenCentral() }

                dependencies {
                    compile 'com.google.guava:guava:19.0'
                }
            }
        """

        def subproject = addSubproject('sub')
        createJavaSourceFile(subproject, main)

        then:
        runTasksSuccessfully('sub:compileJava', 'fixGradleLint')
        dependencies(buildFile, 'compile') == ['com.google.guava:guava:19.0']
    }

    @Issue('46')
    def 'do not move compileOnly dependencies to other configurations'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'war'
            }

            gradleLint.rules = ['unused-dependency']

            repositories { mavenCentral() }

            dependencies {
                compileOnly 'com.google.guava:guava:19.0'
            }
        """

        createJavaSourceFile('''
            import com.google.common.collect.*;
            public class A {
                Object m = HashMultimap.create();
            }
        ''')

        then:
        def results = runTasksSuccessfully('compileJava', 'lintGradle')
        !results.output.contains('unused-dependency')
    }

    @Issue('53')
    def 'only one violation reported when a dependency is unused by multiple configurations'() {
        when:
        buildFile << '''
            plugins {
                id 'nebula.lint'
                id 'java'
                id 'nebula.integtest' version '5.1.2'
            }
            
            repositories { mavenCentral() }
            
            dependencies {
                testCompile 'junit:junit:4.11'
            }
            
            gradleLint.rules = ['all-dependency']
        '''

        createJavaSourceFile('''
            public class Calculator {
                public int add(int x, int y) {
                    return x + y;
                }
            }
        ''')
        createJavaTestFile('''
            import org.junit.Test;
            
            import static org.hamcrest.core.Is.is;
            import static org.junit.Assert.assertThat;
            
            public class CalculatorTest {
            
                @Test
                public void shouldAdd() {
                    Calculator calc = new Calculator();
                    int actual = calc.add(4, 10);
                    assertThat(actual, is(14));
                }
            }
        ''')
        createJavaFile(projectDir, '''
            import org.junit.Test;
            import static org.hamcrest.core.Is.is;
            import static org.junit.Assert.assertThat;
            
            public class CalculatorIntegrationTest {
                @Test
                public void shouldAdd() {
                    Calculator calc = new Calculator();
                    int actual = calc.add(4, 10);
                    assertThat(actual, is(14));
                }
            }
        ''', 'src/integTest/java')

        then:
        def results = runTasksSuccessfully('classes', 'testClasses')
        println(results.output)
        results.output.readLines().count { it.contains('unused-dependency') } == 1
    }


    @Issue("258")
    def 'does not fail with dependency constraints'() {
        setup:
        def expectedWarnings = [
                'warning   unused-dependency                  this dependency is unused and can be removed'
        ]

        when:
        buildFile.text = """\
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            
            gradleLint.rules = ['unused-dependency']
            
            repositories { mavenCentral() }
            
            dependencies {
            implementation 'com.google.guava:guava:18.0'
            implementation 'org.apache.httpcomponents:httpclient'
            constraints {
                implementation('org.apache.httpcomponents:httpclient:4.5.3') {
                    because 'previous versions have a bug impacting this application'
                }
                implementation('commons-codec:commons-codec:1.11') {
                    because 'version 1.9 pulled from httpclient has bugs affecting this application'
                }
            }
}""".stripMargin()

        createJavaSourceFile(main)

        def result = runTasksSuccessfully('compileJava', 'autoLintGradle')
        then:
        expectedWarnings.each {
            assert result.output.contains(it)
        }
    }

}
