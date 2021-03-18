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


import org.junit.Test
import spock.lang.Specification
import spock.lang.Unroll

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Collectors

class ShadedArtifactsTest extends Specification implements AbstractShadedDependencies {
    private File pomFile
    private JarFile jarFile

    def setup() {
        given:
        File jarDir = new File("build/libs")
        File pomDir = new File("build/publications/nebula")

        then:
        jarDir.exists()
        pomDir.exists()

        when:
        File jar = jarDir.listFiles()
                .findAll { it -> it.getName().endsWith(".jar") }
                .findAll { !it.getName().contains("sources") && !it.getName().contains("javadoc") && !it.getName().contains("groovydoc") }
                ?.first()

        pomFile = pomDir.listFiles()
                .findAll { it.getName().endsWith(".xml") }
                ?.first()

        then:
        pomFile.exists()
        jar.exists()

        when:
        jarFile = new JarFile(jar.getAbsolutePath())
    }

    @Test
    def 'inspecting the correct file'() {
        expect:
        assert pomFile.text.contains('<?xml version="1.0" encoding="UTF-8"?>')
    }

    @Test
    def 'jar contains project classes'() {
        // This is more accurate when the test depends on the project actually publishing locally.
        def matchingPath = "com/netflix/nebula/lint"
        def baselineEntries = findFilesInJarMatchingPath(matchingPath)

        // finding the HelloWorld file
        def projectClassFiles = baselineEntries
                .findAll { !it.name.matches(/com\/netflix\/nebula\/lint\/.*\/.*/) }
                .findAll { it.name.endsWith('.class') }

        expect:
        assert projectClassFiles.size() >= 1, "Jar should contain project classes in: $matchingPath"
    }

    @Unroll
    @Test
    def 'pom file does not contain shaded dependencies'() {
        expect:
        assert !pomFile.text.contains("<groupId>${shadedCoordinate.artifactGroup}</groupId>"), "Metadata file should not contain shaded dependency ${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"
        assert !pomFile.text.contains("<artifactId>${shadedCoordinate.artifactName}</artifactId>"), "Metadata file should not contain shaded dependency ${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"

        where:
        shadedCoordinate << SHARED_COORDINATES
    }

    @Unroll
    @Test
    def 'jar contains shaded and relocated dependency'() {
        def matchingPath = shadedCoordinate.relocatedPackageGroup
        def filteredEntries = findFilesInJarMatchingPath(matchingPath)

        expect:
        assert filteredEntries.size() >= 1, "Jar should contain shaded and relocated dependencies: $matchingPath"

        where:
        shadedCoordinate << SHARED_COORDINATES
    }

    @Unroll
    @Test
    def 'jar does not contain non-relocated dependencies'() {
        def matchingPath = shadedCoordinate.originalPackageGroup
        def filteredEntries = findFilesInJarMatchingPath(matchingPath)

        expect:
        assert filteredEntries.size() == 0, "Jar should not contain non relocated dependencies: $matchingPath"

        where:
        shadedCoordinate << SHARED_COORDINATES
    }

    @Test
    def pomFileDoesNotContainDependenciesThatActAsProvidedScope() {
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
