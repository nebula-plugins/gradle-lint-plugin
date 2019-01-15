/*
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

package com.netflix.nebula.lint.rule.dependency.provider

import com.netflix.nebula.lint.TestKitSpecification
import org.gradle.api.artifacts.Dependency
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import javax.annotation.Nullable

class ClasspathBasedRecommendationProviderTest extends TestKitSpecification {
    static def version = 1.0

    @Unroll
    def "finds single bom when listed as a compile-time dependency - as #desc"() {
        given:
        def project = ProjectBuilder.builder().build()

        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        project.repositories { maven { url repo } }

        // demonstrates maven property interpolation from gradle project properties
        project.getExtensions().add("commonsVersion", "1.1.2")

        project.dependencies {
            compile module
        }

        def provider = new MavenBomRecommendationProvider(project)

        when:
        def files = provider.getBomsOnConfiguration()

        then:
        files.find { it.toString().find(/sample\/recommender\/1.0\/recommender-1.0.pom/) }

        where:
        module                            | desc
        'sample:recommender:1.0'          | 'default'
        "sample:recommender:$version"     | 'interpolation' // verify GString doesn't cause issues
        'sample:recommender:1.0@pom'      | '@pom'
        "sample:recommender:$version@pom" | '@pom and interpolation'
    }

    @Unroll
    def "finds multiple boms when listed as compile-time dependencies - as #desc"() {
        given:
        def project = ProjectBuilder.builder().build()

        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')
        setupSampleBomFile(repo, 'fizz')
        setupSampleBomFile(repo, 'buzz')

        project.repositories { maven { url repo } }

        // demonstrates maven property interpolation from gradle project properties
        project.getExtensions().add("commonsVersion", "1.1.2")

        project.dependencies {
            compile firstBom
            compile secondBom
            compile thirdBom
        }

        def provider = new MavenBomRecommendationProvider(project)

        when:
        def files = provider.getBomsOnConfiguration()

        then:
        files.find { it.toString().contains(/sample\/recommender\/1.0\/recommender-1.0.pom/) }
        files.find { it.toString().contains(/sample\/fizz\/1.0\/fizz-1.0.pom/) }
        files.find { it.toString().contains(/sample\/buzz\/1.0\/buzz-1.0.pom/) }

        where:
        firstBom                          | secondBom                  | thirdBom                   | desc
        'sample:recommender:1.0'          | 'sample:fizz:1.0'          | 'sample:buzz:1.0'          | 'default'
        "sample:recommender:$version"     | "sample:fizz:$version"     | "sample:buzz:$version"     | 'interpolation' // verify GString doesn't cause issues
        'sample:recommender:1.0@pom'      | 'sample:fizz:1.0@pom'      | 'sample:buzz:1.0@pom'      | '@pom'
        "sample:recommender:$version@pom" | "sample:fizz:$version@pom" | "sample:buzz:$version@pom" | '@pom and interpolation'
    }

    @Unroll
    def "finds multiple boms when listed as different configurations in dependencies - as #desc"() {
        given:
        def project = ProjectBuilder.builder().build()

        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')
        setupSampleBomFile(repo, 'fizz')
        setupSampleBomFile(repo, 'buzz')

        project.repositories { maven { url repo } }

        // demonstrates maven property interpolation from gradle project properties
        project.getExtensions().add("commonsVersion", "1.1.2")

        project.dependencies {
            compile firstBom
            runtime secondBom
            testCompile thirdBom
        }

        def provider = new MavenBomRecommendationProvider(project)

        when:
        def files = provider.getBomsOnConfiguration()

        then:
        files.find { it.toString().contains(/sample\/recommender\/1.0\/recommender-1.0.pom/) }
        files.find { it.toString().contains(/sample\/fizz\/1.0\/fizz-1.0.pom/) }
        files.find { it.toString().contains(/sample\/buzz\/1.0\/buzz-1.0.pom/) }

        where:
        firstBom                          | secondBom                  | thirdBom                   | desc
        'sample:recommender:1.0'          | 'sample:fizz:1.0'          | 'sample:buzz:1.0'          | 'default'
        "sample:recommender:$version"     | "sample:fizz:$version"     | "sample:buzz:$version"     | 'interpolation' // verify GString doesn't cause issues
        'sample:recommender:1.0@pom'      | 'sample:fizz:1.0@pom'      | 'sample:buzz:1.0@pom'      | '@pom'
        "sample:recommender:$version@pom" | "sample:fizz:$version@pom" | "sample:buzz:$version@pom" | '@pom and interpolation'
    }


    @Unroll
    def "ignores files that are not pom files in dependencies - as #desc"() {
        given:
        def project = ProjectBuilder.builder().build()

        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')
        setupSampleBomFile(repo, 'fizz')
        setupSampleBomFile(repo, 'buzz')
        setupSampleArtifactPomFile(repo, 'bat')

        project.repositories { maven { url repo } }

        // demonstrates maven property interpolation from gradle project properties
        project.getExtensions().add("commonsVersion", "1.1.2")

        project.dependencies {
            compile firstBom
            compile 'sample:bat:latest.release'
            runtime secondBom
            testCompile thirdBom
        }

        def provider = new MavenBomRecommendationProvider(project)

        when:
        def files = provider.getBomsOnConfiguration()

        then:
        files.find { it.toString().contains(/sample\/recommender\/1.0\/recommender-1.0.pom/) }
        files.find { it.toString().contains(/sample\/fizz\/1.0\/fizz-1.0.pom/) }
        files.find { it.toString().contains(/sample\/buzz\/1.0\/buzz-1.0.pom/) }
        !files.find { it.toString().contains(/bat/) }

        where:
        firstBom                          | secondBom                  | thirdBom                   | desc
        'sample:recommender:1.0'          | 'sample:fizz:1.0'          | 'sample:buzz:1.0'          | 'default'
        "sample:recommender:$version"     | "sample:fizz:$version"     | "sample:buzz:$version"     | 'interpolation' // verify GString doesn't cause issues
        'sample:recommender:1.0@pom'      | 'sample:fizz:1.0@pom'      | 'sample:buzz:1.0@pom'      | '@pom'
        "sample:recommender:$version@pom" | "sample:fizz:$version@pom" | "sample:buzz:$version@pom" | '@pom and interpolation'
    }

    def "returns true when dependency is already added"() {
        given:
        def project = ProjectBuilder.builder().build()

        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'fizz')
        setupSampleBomFile(repo, 'buzz')
        setupSampleBomFile(repo, 'bat')

        project.repositories { maven { url repo } }

        project.dependencies {
            compile 'sample:fizz:1.0'
            compile 'sample:bat:1.0'
            compile 'sample:buzz:1.0'
        }

        Dependency dep = newDependency('bat')
        def boms = new HashSet()
        boms.add(new File('sample/fizz/1.0/fizz-1.0.pom'))
        boms.add(new File('sample/buzz/1.0/buzz-1.0.pom'))
        boms.add(new File('sample/bat/1.0/bat-1.0.pom'))

        when:
        def provider = new MavenBomRecommendationProvider(project)
        def isAlreadyAdded = provider.dependencyIsAlreadyAdded(dep, boms)

        then:
        isAlreadyAdded
    }

    def "returns false when dependency is new"() {
        given:
        def project = ProjectBuilder.builder().build()

        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'fizz')
        setupSampleBomFile(repo, 'buzz')
        setupSampleBomFile(repo, 'bat')

        project.repositories { maven { url repo } }

        project.dependencies {
            compile 'sample:fizz:1.0'
            compile 'sample:bat:1.0'
            compile 'sample:buzz:1.0'
        }

        Dependency dep = newDependency('bat')
        def boms = new HashSet()
        boms.add(new File('sample/fizz/1.0/fizz-1.0.pom'))
        boms.add(new File('sample/buzz/1.0/buzz-1.0.pom'))

        when:
        def provider = new MavenBomRecommendationProvider(project)
        def isAlreadyAdded = provider.dependencyIsAlreadyAdded(dep, boms)

        then:
        !isAlreadyAdded
    }

    def "returns true when file should be added to bom"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'fizz')
        def fizzPom = new File(repo, 'sample/fizz/1.0/fizz-1.0.pom')

        when:
        def provider = new MavenBomRecommendationProvider(project)
        def shouldAddToBoms = provider.shouldAddToBoms(fizzPom)

        then:
        shouldAddToBoms
    }

    def "returns false when file should not be added to bom - not a pom"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        def sample = new File(repo, 'sample/bat/1.0')
        sample.mkdirs()
        def sampleFile = new File(sample, 'bat-1.0.txt')
        sampleFile << "I am not a pom!"

        when:
        def provider = new MavenBomRecommendationProvider(project)
        def shouldAddToBoms = provider.shouldAddToBoms(sampleFile)

        then:
        !shouldAddToBoms
    }

    def "returns false when file should not be added to bom - different packaging"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'

        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleArtifactPomFile(repo, 'buzz')
        def buzzPom = new File(repo, 'sample/buzz/1.0/buzz-1.0.pom')

        when:
        def provider = new MavenBomRecommendationProvider(project)
        def shouldAddToBoms = provider.shouldAddToBoms(buzzPom)

        then:
        !shouldAddToBoms
    }

    private static Dependency newDependency(String name) {
        Dependency dep = new Dependency() {
            @Override
            String getGroup() {
                return 'sample'
            }

            @Override
            String getName() {
                return name
            }

            @Override
            String getVersion() {
                return '1.0'
            }

            @Override
            boolean contentEquals(Dependency dependency) {
                return false
            }

            @Override
            Dependency copy() {
                return null
            }

            void because(@Nullable String s) {}

            String getReason() {
                return ""
            }
        }
        dep
    }

    private static void setupSampleBomFile(File repo, String artifactName) {
        def sampleFileContents = """\
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>sample</groupId>
              <artifactId>${artifactName}</artifactId>
              <version>1.0</version>
              <packaging>pom</packaging>

              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>commons-logging</groupId>
                    <artifactId>commons-logging</artifactId>
                    <version>1.1.1</version>
                  </dependency>
                  <dependency>
                    <groupId>commons-configuration</groupId>
                    <artifactId>commons-configuration</artifactId>
                    <version>\${commonsVersion}</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
        """
        setupSampleFileWith(repo, artifactName, sampleFileContents)
    }

    private static void setupSampleArtifactPomFile(File repo, String artifactName) {
        def sampleFileContents = """\
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>sample</groupId>
              <artifactId>${artifactName}</artifactId>
              <version>1.0</version>

              <dependencies>
                <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>\${junitVersion}</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
              
              <properties>
                <junitVersion>3.8.1</junitVersion>
              </properties>
            </project>
        """
        setupSampleFileWith(repo, artifactName, sampleFileContents)
    }

    private static File setupSampleFileWith(File repo, String artifactName, String sampleFileContents) {
        def sample = new File(repo, 'sample/' + artifactName + '/1.0')
        sample.mkdirs()
        def sampleFile = new File(sample, artifactName + '-1.0.pom')
        sampleFile << sampleFileContents
    }
}
