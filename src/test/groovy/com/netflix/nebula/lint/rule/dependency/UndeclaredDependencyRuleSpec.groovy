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
import nebula.test.dependencies.Coordinate
import nebula.test.dependencies.maven.Pom
import spock.lang.Subject
import spock.lang.Unroll

@Subject(UndeclaredDependencyRule)
class UndeclaredDependencyRuleSpec extends TestKitSpecification {
    private static final def sample = new Coordinate('sample', 'alpha', '1.0')
    private static final def commonsLogging = new Coordinate('commons-logging', 'commons-logging', '1.2')
    private static final def commonsLang = new Coordinate('commons-lang', 'commons-lang', '2.6')
    private static final def lombok = new Coordinate('org.projectlombok', 'lombok', '1.18.10')
    private static final def junit = new Coordinate('junit', 'junit', '4.12')

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

    def setup() {
        definePluginOutsideOfPluginBlock = true
    }

    @Unroll
    def 'simple - add undeclared dependencies as first-orders for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSimpleSetup(repo, configuration == 'api')
        buildFile << """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()

        when:

        createJavaSourceFile(main)

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
        }
        assert buildFile.text.contains("$configuration '${commonsLogging.toString()}'")

        where:
        deps     | expected                 | configuration
//        [sample] | [sample, commonsLogging] | 'implementation'
        [sample] | [sample, commonsLogging] | 'compile'
//        [sample] | [sample, commonsLogging] | 'api'
    }

    @Unroll
    def 'adds dependencies alphabetically for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(lombok.getGroup(), lombok.getArtifact(), lombok.getVersion())
        samplePom.addDependency(junit.getGroup(), junit.getArtifact(), junit.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        samplePom.addDependency(commonsLang.getGroup(), commonsLang.getArtifact(), commonsLang.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSimpleSetup(repo)
        buildFile << """\
            
            buildscript {
              repositories {
                maven {
                  url "https://plugins.gradle.org/m2/"
                }
              }
              dependencies {
                classpath "io.franzbecker:gradle-lombok:3.2.0"
              }
            }
            
            apply plugin: "io.franzbecker.gradle-lombok"

            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()

        when:

        createJavaSourceFile("""\
            import lombok.Value;
            import org.apache.commons.lang.StringUtils;
            import org.apache.commons.logging.Log;
            import org.apache.commons.logging.LogFactory;            
            public final class Main {
                public static void main(String[] args) {
                    Log log = LogFactory.getLog(Main.class);
                    final Friend f = new Friend("bat", "bar");
                    if(!StringUtils.isEmpty(f.getFirstName())) {
                        log.info(f.getFirstName());
                    }
                }
                @Value
                private static class Friend {
                    private final String firstName;
                    private final String lastName;
                }
            }
            """.stripIndent())

        createJavaTestFile(projectDir, '''
            import static org.junit.Assert.*;
            import org.junit.Test;

            public class MainTest {
                @Test
                public void performTest() {
                    assertEquals(1, 1);
                }
            }
        '''.stripIndent())

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
        }

        def expectedOrderingForDependencies = """\
            dependencies {
                $configuration '${sample.toString()}'
                $configuration '${commonsLang.toString()}'
                $configuration '${commonsLogging.toString()}'
                test${configuration.capitalize()} '${junit.toString()}'
            }
            """.stripIndent()
        buildFile.text.contains(expectedOrderingForDependencies)

        where:
        deps     | expected                                     | configuration
        [sample] | [sample, commonsLogging, junit, commonsLang] | 'compile'
//        [sample] | [sample, commonsLogging, junit, commonsLang] | 'implementation'
    }

    @Unroll
    def 'adds from even transitive runtime scope for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSimpleSetup(repo)
        buildFile << """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()

        when:

        createJavaSourceFile(main)

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
        }
        assert buildFile.text.contains("$configuration '${commonsLogging.toString()}'")

        where:
        deps     | expected                 | configuration
//        [sample] | [sample, commonsLogging] | 'implementation'
        [sample] | [sample, commonsLogging] | 'compile'
    }

    @Unroll
    def 'when defined dynamically, resolve to static version for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), 'latest.release')
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSimpleSetup(repo)
        buildFile << """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()

        when:

        createJavaSourceFile(main)

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
        }
        assert buildFile.text.contains("$configuration '${sample.toString()}'")
        assert buildFile.text.contains("$configuration '${commonsLogging.toString()}'")

        where:
        deps     | expected                 | configuration
//        [sample] | [sample, commonsLogging] | 'implementation'
        [sample] | [sample, commonsLogging] | 'compile'
    }

    @Unroll
    def 'adds when required in test code for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(junit.getGroup(), junit.getArtifact(), junit.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSimpleSetup(repo)
        buildFile << """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()

        when:
        createJavaTestFile(projectDir, '''
            import static org.junit.Assert.*;
            import org.junit.Test;

            public class MainTest {
                @Test
                public void performTest() {
                    assertEquals(1, 1);
                }
            }
        '''.stripIndent())

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
        }
        assert buildFile.text.contains("$configuration '${sample.toString()}'")
        assert buildFile.text.contains("$configuration '${junit.toString()}'")

        where:
        deps     | expected        | configuration
//        [sample] | [sample, junit] | 'testImplementation'
        [sample] | [sample, junit] | 'testCompile'
    }

    @Unroll
    def 'adds when required through the type hierarchy for #configuration configuration'() {
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSimpleSetup(repo)
        buildFile << """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()

        when:
        createJavaSourceFile('''
            import org.apache.commons.logging.impl.NoOpLog;

            public abstract class Main extends NoOpLog {
            }
        '''.stripIndent())

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
        }

        where:
        deps     | expected                 | configuration
        [sample] | [sample, commonsLogging] | 'compile'
//        [sample] | [sample, commonsLogging] | 'implementation'
    }

    @Unroll
    def 'adds when defining plugin in plugin block for #configuration configuration'() {
        definePluginOutsideOfPluginBlock = false
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        when:
        buildFile.text = """\
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['undeclared-dependency']

            repositories {
                maven { url "${repo.toURI().toURL()}" }
                mavenCentral()
            }

            dependencies {
            ${deps.collect { "   $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()

        createJavaSourceFile(main)

        then:
        def result = runTasksSuccessfully('fixGradleLint', '--warning-mode', 'none')

        result.output.contains('fixed')
        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
        }
        assert buildFile.text.contains("$configuration '${sample.toString()}'")
        assert buildFile.text.contains("$configuration '${commonsLogging.toString()}'")

        where:
        deps     | expected                 | configuration
//        [sample] | [sample, commonsLogging] | 'implementation'
        [sample] | [sample, commonsLogging] | 'compile'
    }

    @Unroll
    def 'when defined in allprojects block - adds dependencies to smallest scope for subprojects for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        def dependencyBlock = """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent()
        setupBuildGradleSetupForAllprojects(repo, dependencyBlock)

        addSubproject('sub1', """\
            dependencies {
            }
            """.stripIndent())

        when:

        createJavaSourceFile(new File(projectDir.toString() + File.separator + 'sub1'), main)

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        assert dependencies(buildFile).contains(sample.toString())
        assert dependencies(new File(projectDir, 'sub1/build.gradle')).contains(commonsLogging.toString())

        where:
        deps     | expected                 | configuration
//        [sample] | [sample, commonsLogging] | 'implementation'
        [sample] | [sample, commonsLogging] | 'compile'
    }

    @Unroll
    def 'when defined in parent build file - adds dependencies to smallest scope for subprojects for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSetupForAllprojects(repo)
        buildFile << """\
            subprojects {
                dependencies {
                ${deps.collect { "    $configuration '$it'" }.join('\\n')}
                }
            }
            """.stripIndent()

        addSubproject('sub1', """\
            dependencies {
            }
            """.stripIndent())

        when:

        createJavaSourceFile(new File(projectDir.toString() + File.separator + 'sub1'), main)

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')
        assert dependencies(buildFile).contains(sample.toString())
        assert dependencies(new File(projectDir, 'sub1/build.gradle')).contains(commonsLogging.toString())

        where:
        deps     | expected                 | configuration
//        [sample] | [sample, commonsLogging] | 'implementation'
        [sample] | [sample, commonsLogging] | 'compile'
    }

    @Unroll
    def 'when defined in subproject build files - adds dependencies to smallest scope for subprojects for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSetupForAllprojects(repo)

        addSubproject('sub1', """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent())

        addSubproject('sub2', """\
            dependencies {
            ${deps.collect { "    $configuration '$it'" }.join('\n')}
            }
            """.stripIndent())

        when:

        createJavaSourceFile(new File(projectDir.toString() + File.separator + 'sub1'), main)
        createJavaSourceFile(new File(projectDir.toString() + File.separator + 'sub2'), main)

        then:
        def result = runTasksSuccessfully('fixGradleLint')

        result.output.contains('fixed')

        def sub1Build = new File(projectDir, 'sub1/build.gradle')
        def sub1Dependencies = dependencies(sub1Build)
        for (def expectedDependency : expected) {
            assert sub1Dependencies.contains(expectedDependency.toString())
        }

        def sub2Build = new File(projectDir, 'sub2/build.gradle')
        def sub2Dependencies = dependencies(sub2Build)
        for (def expectedDependency : expected) {
            assert sub2Dependencies.contains(expectedDependency.toString())
        }
        where:
        deps     | expected                 | configuration
//        [sample] | [sample, commonsLogging] | 'implementation'
        [sample] | [sample, commonsLogging] | 'compile'
    }

    @Unroll
    def 'handles empty subprojects build file for #configuration configuration'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(commonsLogging.getGroup(), commonsLogging.getArtifact(), commonsLogging.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSetupForAllprojects(repo)
        buildFile << """\
            subprojects {
                dependencies {
                ${deps.collect { "    $configuration '$it'" }.join('\\n')}
                }
            }
            """.stripIndent()
        addSubproject('sub1', "")

        when:

        createJavaSourceFile(new File(projectDir.toString() + File.separator + 'sub1'), main)

        then:
        def firstResult = runTasksSuccessfully('fixGradleLint')

        firstResult.output.contains('you require a dependencies block')

        when:
        def secondResult = runTasksSuccessfully('fixGradleLint')

        then:
        secondResult
        secondResult.output.contains('fixed')
        assert dependencies(buildFile).contains(sample.toString())
        assert dependencies(new File(projectDir, 'sub1/build.gradle')).contains(commonsLogging.toString())

        where:
        deps   | configuration
//        sample | 'implementation'
        sample | 'compile'
    }

    def 'when using compileOnly configuration, transitives are resolved before linting so no changes are made'() {
        given:
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(lombok.getGroup(), lombok.getArtifact(), lombok.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        setupBuildGradleSimpleSetup(repo)
        buildFile << """\

            buildscript {
              repositories {
                maven {
                  url "https://plugins.gradle.org/m2/"
                }
              }
              dependencies {
                classpath "io.franzbecker:gradle-lombok:3.2.0"
              }
            }
            
            apply plugin: "io.franzbecker.gradle-lombok"
            
            dependencies {
            ${deps.collect { "    compileOnly '$it'" }.join('\n')}
            }
            """.stripIndent()

        when:

        createJavaSourceFile("""\
            import lombok.Value;
            public final class Main {
                public static void main(String[] args) {
                    final Friend f = new Friend("bat", "bar");
                    System.out.println(f.getFirstName());
                }
                @Value
                private static class Friend {
                    private final String firstName;
                    private final String lastName;
                }
            }
            """.stripIndent())

        then:
        runTasksSuccessfully('fixGradleLint')

        def dependencies = dependencies(buildFile)
        for (def expectedDependency : expected) {
            assert dependencies.contains(expectedDependency.toString())
            assert buildFile.text.contains("compileOnly '${expectedDependency.toString()}'")
        }

        where:
        deps     | expected
        [sample] | [sample]
    }

    def setupBuildGradleSimpleSetup(File repo, boolean usesJavaLibraryPlugin = false) {
        String languagePlugin = usesJavaLibraryPlugin ? 'java-library' : 'java'

        buildFile << """\
            apply plugin: 'nebula.lint'
            apply plugin: '$languagePlugin'
            gradleLint.rules = ['undeclared-dependency']
            repositories {
                maven { url "${repo.toURI().toURL()}" }
                mavenCentral()
            }
            """.stripIndent()
    }

    def setupBuildGradleSetupForAllprojects(File repo, String dependencyBlock = '') {
        buildFile << """\
            allprojects {
                apply plugin: 'nebula.lint'
                apply plugin: 'java'
                gradleLint.rules = ['undeclared-dependency']
                repositories {
                    maven { url "${repo.toURI().toURL()}" }
                    mavenCentral()
                }
            """.stripIndent()
        buildFile << dependencyBlock
        buildFile << """\
            }
            """.stripIndent()
    }
}
