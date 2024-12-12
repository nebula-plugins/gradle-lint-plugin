/**
 *
 *  Copyright 2018-2019 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import nebula.test.dependencies.Coordinate
import nebula.test.dependencies.maven.Pom
import spock.lang.Unroll

class SupportJavaLibrarySpec extends BaseIntegrationTestKitSpec {
    private static final def sample = new Coordinate('sample', 'alpha', '1.0')
    private static final def junit = new Coordinate('junit', 'junit', '4.11')
    def repo

    def setup() {
        repo = new File(projectDir, 'repo')
        repo.mkdirs()

        def samplePom = new Pom(sample.getGroup(), sample.getArtifact(), sample.getVersion())
        samplePom.addDependency(junit.getGroup(), junit.getArtifact(), junit.getVersion())
        ArtifactHelpers.setupSamplePomWith(repo, sample, samplePom.generate())
        ArtifactHelpers.setupSampleJar(repo, sample)

        definePluginOutsideOfPluginBlock = true
    }

    @Unroll
    def 'handles java-library plugin with dependency on #configuration'() {
        given:
        setupWithJavaLibraryAndAllRules(configuration)

        when:
        if (configuration == 'testCompile') {
            System.setProperty("ignoreDeprecations", "true")
        }
        def result = runTasks('fixGradleLint')
        if (configuration == 'testCompile') {
            System.setProperty("ignoreDeprecations", "false")
        }

        then:
        result.output.contains('This project contains lint violations.')
        result.output.contains('unused-dependency')

        def sub1BuildFileText = new File("$projectDir/sub1", 'build.gradle').text
        def expectedDependencies = """
            dependencies {
            }
            """.stripIndent()
        sub1BuildFileText.contains(expectedDependencies)

        where:
        configuration << ['testImplementation', 'implementation']
    }

    @Unroll
    def 'undeclared dependency - handles java-library plugin with dependency on #configuration'() {
        given:
        setupWithJavaLibraryAndAllRules(configuration, true)

        when:
        if (configuration == 'testCompile') {
            System.setProperty("ignoreDeprecations", "true")
        }
        def result = runTasks('fixGradleLint')
        if (configuration == 'testCompile') {
            System.setProperty("ignoreDeprecations", "false")
        }

        then:
        result.output.contains('This project contains lint violations.')
        result.output.contains('undeclared-dependency')
        result.output.contains('unused-dependency')

        def sub1BuildFileText = new File("$projectDir/sub1", 'build.gradle').text
        def expectedDependencies = """
            dependencies {
                testImplementation '$junit'
            }
            """.stripIndent()
        sub1BuildFileText.contains(expectedDependencies)

        where:
        configuration << ['testImplementation', 'implementation']
    }

    private def setupWithJavaLibraryAndAllRules(String configuration, boolean undeclaredDependency = false) {
        def deps = [sample]
        if (!undeclaredDependency) {
            deps.add(junit)
        }

        buildFile << """
        |allprojects {
        |    apply plugin: 'nebula.lint'
        |    apply plugin: 'java-library'
        |    dependencies {
        |    ${deps.collect { "    $configuration '$it'" }.join('\n\t')}
        |    }
        |    gradleLint.rules=['all-dependency']
        |    repositories {
        |        maven { url = "${repo.toURI().toURL()}" }
        |        mavenCentral()
        |    }
        |}
        |""".stripMargin()

        addSubproject('sub1', """
            dependencies {
                api 'com.squareup.retrofit2:retrofit:2.4.0'
                implementation 'com.squareup.okhttp3:okhttp:3.10.0'
            }
            """.stripIndent())
        writeHelloWorld(new File(projectDir.toString() + '/sub1'))
        writeUnitTest(new File(projectDir.toString() + '/sub1'))
    }
}
