/**
 *
 *  Copyright 2020 Netflix, Inc.
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

package com.netflix.nebula.lint.self

import nebula.test.IntegrationTestKitSpec
import org.junit.Test

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Collectors

class ShadedArtifactsTest extends IntegrationTestKitSpec implements AbstractShadedDependencies {
    private File pomFile
    private JarFile jarFile
    private List<File> gradleModuleMetadataFiles

    def setup() {
        debug = true
        keepFiles = true

        given:
        buildFile.text = new File('build.gradle').text
        buildFile << """
            publishing {
                repositories {
                    maven {
                        name = 'testLocal'
                        url = 'testrepo'
                    }
                }
            }
            tasks.named('gpgSignVersion').configure {
                it.enabled = false
            }
            tasks.named('publishVersionToBintray').configure {
                it.enabled = false
            }
            tasks.named('syncVersionToMavenCentral').configure {
                it.enabled = false
            }
            """.stripIndent()
        writeHelloWorld('com.netflix.nebula.lint')

        when:
        System.setProperty("ignoreDeprecations", "true")
        runTasks('shadowJar', 'publishNebulaPublicationToTestLocalRepository')
        System.setProperty("ignoreDeprecations", "false")

        File groupDir = new File(projectDir, "testrepo/com/netflix/nebula")
        File artifactDir = new File(groupDir, moduleName)

        File dir = artifactDir.listFiles()
                .findAll { it.isDirectory() }
                ?.first()
        then:
        dir.exists()

        when:
        pomFile = dir.listFiles()
                .findAll { it.getName().endsWith(".pom") }
                ?.first()

        File jar = dir.listFiles()
                .findAll { it -> it.getName().endsWith(".jar") }
                .findAll { !it.getName().contains("sources") && !it.getName().contains("javadoc") }
                ?.first()

        gradleModuleMetadataFiles = dir.listFiles()
                .findAll { it -> it.getName().endsWith(".module") }

        then:
        pomFile.exists()
        jar.exists()

        when:
        jarFile = new JarFile(jar.getAbsolutePath())
    }

    @Test
    def 'metadata and jar files contain correct dependencies'() {
        expect:
        jarContainsProjectClasses()
        doNotPublishGradleModuleMetadataWithShadedArtifacts()
        assert pomFile.text.contains('<url>ssh://git@github.com/nebula-plugins/gradle-lint-plugin.git</url>')

        shadedCoordinates.each { shadedCoordinate ->
            pomFileDoesNotContainShadedDependencies(shadedCoordinate)
            jarContainsShadedAndRelocatedDirectDependency(shadedCoordinate)
            jarDoesNotContainNonRelocatedDependencies(shadedCoordinate)
        }

        pomFileDoesNotContainDependenciesThatActAsProvidedScope()
    }

    private void pomFileDoesNotContainShadedDependencies(ShadedCoordinate shadedCoordinate) {
        assert !pomFile.text.contains("<groupId>${shadedCoordinate.artifactGroup}</groupId>"), "Metadata file should not contain shaded dependency ${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"
        assert !pomFile.text.contains("<artifactId>${shadedCoordinate.artifactName}</artifactId>"), "Metadata file should not contain shaded dependency ${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"
    }

    private void jarContainsProjectClasses() {
        // This is more accurate when the test depends on the project actually publishing locally.
        def matchingPath = "com/netflix/nebula/"
        def baselineEntries = findFilesInJarMatchingPath(matchingPath)

        // finding the HelloWorld file
        def projectClassFiles = baselineEntries
                .findAll { !it.name.matches(/com\/netflix\/nebula\/lint\/.*\/.*/) }
                .findAll { it.name.endsWith('.class') }

        assert projectClassFiles.size() >= 1, "Jar should contain project classes in: $matchingPath"
    }

    private void jarContainsShadedAndRelocatedDirectDependency(ShadedCoordinate shadedCoordinate) {
        def matchingPath = shadedCoordinate.relocatedPackageGroup
        def filteredEntries = findFilesInJarMatchingPath(matchingPath)

        assert filteredEntries.size() >= 1, "Jar should contain shaded and relocated dependencies: $matchingPath"
    }

    private void jarDoesNotContainNonRelocatedDependencies(ShadedCoordinate shadedCoordinate) {
        def matchingPath = shadedCoordinate.originalPackageGroup
        def filteredEntries = findFilesInJarMatchingPath(matchingPath)

        assert filteredEntries.size() == 0, "Jar should not contain non relocated dependencies: $matchingPath"
    }

    private void doNotPublishGradleModuleMetadataWithShadedArtifacts() {
        assert gradleModuleMetadataFiles.size() == 0, "Gradle module metadata does not allow for removing shaded dependencies"
    }

    private void pomFileDoesNotContainDependenciesThatActAsProvidedScope() {
        assert !pomFile.text.contains('<artifactId>spock-core</artifactId>')
        assert !pomFile.text.contains('<artifactId>nebula-test</artifactId>')
        assert !pomFile.text.contains('<artifactId>junit</artifactId>')
    }

    private List<JarEntry> findFilesInJarMatchingPath(String path) {
        String updatedPath = path.replace('.', '/')

        List<JarEntry> baselineEntries = jarFile.stream()
                .filter({ jarEntry ->
                    jarEntry.getName().startsWith(updatedPath)
                })
                .collect(Collectors.toList()) as List<JarEntry>
        baselineEntries
    }

}
