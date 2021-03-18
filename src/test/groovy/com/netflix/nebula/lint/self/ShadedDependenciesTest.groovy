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

class ShadedDependenciesTest extends Specification implements AbstractShadedDependencies {
    File directory = new File("build/reports/project")

    @Test
    def 'compile classpath contains dependencies from shaded and unshaded configurations'() {
        when:
        File report = new File(directory, "compileClasspath-dependencies.txt");
        report.exists()

        then:
        SHARED_COORDINATES.each { shadedCoordinate ->
            String artifactGroupAndName = "${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"
            report.text.contains(artifactGroupAndName)
        }
        report.text.contains('org.apache.maven:maven-model-builder')
    }

    @Test
    def 'runtime classpath contains dependencies from shaded and unshaded configurations'() {
        when:
        File report = new File(directory, "runtimeClasspath-dependencies.txt");
        report.exists()

        then:
        SHARED_COORDINATES.each { shadedCoordinate ->
            String artifactGroupAndName = "${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"
            report.text.contains(artifactGroupAndName)
        }
        report.text.contains('org.apache.maven:maven-model-builder')
    }

    @Test
    def 'test compile classpath contains dependencies from shaded and unshaded configurations'() {
        when:
        File report = new File(directory, "testCompileClasspath-dependencies.txt");
        report.exists()

        then:
        SHARED_COORDINATES.each { shadedCoordinate ->
            String artifactGroupAndName = "${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"
            report.text.contains(artifactGroupAndName)
        }
        report.text.contains('org.apache.maven:maven-model-builder')
    }

    @Test
    def 'test runtime classpath contains dependencies from shaded and unshaded configurations'() {
        when:
        File report = new File(directory, "testRuntimeClasspath-dependencies.txt");
        report.exists()

        then:
        SHARED_COORDINATES.each { shadedCoordinate ->
            String artifactGroupAndName = "${shadedCoordinate.artifactGroup}:${shadedCoordinate.artifactName}"
            report.text.contains(artifactGroupAndName)
        }
        report.text.contains('org.apache.maven:maven-model-builder')
    }
}
