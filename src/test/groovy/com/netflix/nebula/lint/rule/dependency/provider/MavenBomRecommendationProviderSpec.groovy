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

import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.maven.ArtifactType
import nebula.test.dependencies.maven.Pom
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class MavenBomRecommendationProviderSpec extends Specification {
    @Rule
    TemporaryFolder projectDir
    static def version = 1.0

    @Unroll
    def 'recommendations are loaded from the dependencyManagement section of a BOM - #desc'() {
        setup:
        def project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'

        def repo = projectDir.newFolder('repo')
        def sample = new File(repo, 'sample/recommender/1.0')
        sample.mkdirs()
        def sampleFile = new File(sample, 'recommender-1.0.pom')
        sampleFile << '''
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>sample</groupId>
              <artifactId>recommender</artifactId>
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
                    <version>${commonsVersion}</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
        '''

        project.repositories { maven { url repo } }

        // demonstrates maven property interpolation from gradle project properties
        project.getExtensions().add("commonsVersion", "1.1.2")

        project.dependencies {
            compile module
        }

        when:
        def recommendations = new MavenBomRecommendationProvider(project)

        then:
        recommendations.getVersion('commons-logging', 'commons-logging') == '1.1.1'
        recommendations.getVersion('commons-configuration', 'commons-configuration') == '1.1.2'

        where:
        module                            | desc
        'sample:recommender:1.0'          | 'default'
        "sample:recommender:$version"     | 'interpolation' // verify GString doesn't cause issues
        'sample:recommender:1.0@pom'      | '@pom'
        "sample:recommender:$version@pom" | '@pom and interpolation'
    }

    def 'bom files that specify a non-relative parent pom are resolvable'() {
        setup:
        def project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'

        def repo = projectDir.newFolder('repo')

        def parent = new File(repo, 'sample/recommender-parent/1.0')
        parent.mkdirs()
        new File(parent, 'recommender-parent-1.0.pom') << '''
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>sample</groupId>
              <artifactId>recommender-parent</artifactId>
              <version>1.0</version>
              <packaging>pom</packaging>
            </project>
        '''

        def sample = new File(repo, 'sample/recommender/1.1.1')
        sample.mkdirs()
        def samplePom = new File(sample, 'recommender-1.1.1.pom')
        samplePom << '''
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <artifactId>recommender-parent</artifactId>
                <groupId>sample</groupId>
                <version>1.0</version>
              </parent>
              <groupId>sample</groupId>
              <artifactId>recommender</artifactId>
              <version>1.1.1</version>
              <packaging>pom</packaging>

              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>commons-logging</groupId>
                    <artifactId>commons-logging</artifactId>
                    <version>${project.version}</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
        '''

        project.repositories {
            maven { url repo }
        }

        project.dependencies {
            compile 'sample:recommender:1.1.1@pom'
        }

        when:
        def recommendations = new MavenBomRecommendationProvider(project)

        then:
        recommendations.getVersion('commons-logging', 'commons-logging') == '1.1.1'
    }

    @Unroll
    def 'recommendations are loaded from the dependencyManagement section of a BOM with profiles - #desc'() {
        setup:
        def project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'

        def repo = projectDir.newFolder('repo')
        def sample = new File(repo, 'sample/recommender/1.0')
        sample.mkdirs()
        def sampleFile = new File(sample, 'recommender-1.0.pom')
        sampleFile << '''
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>sample</groupId>
              <artifactId>recommender</artifactId>
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
                    <version>${commonsVersion}</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
              <profiles>
                <profile>
                  <id>java7</id>
                  <activation>
                    <jdk>(,1.8)</jdk>
                  </activation>
                </profile>
              </profiles>
            </project>
        '''

        project.repositories { maven { url repo } }

        // demonstrates maven property interpolation from gradle project properties
        project.getExtensions().add("commonsVersion", "1.1.2")

        project.dependencies {
            compile module
        }

        when:
        def recommendations = new MavenBomRecommendationProvider(project)

        then:
        recommendations.getVersion('commons-logging', 'commons-logging') == '1.1.1'
        recommendations.getVersion('commons-configuration', 'commons-configuration') == '1.1.2'

        where:
        module                            | desc
        'sample:recommender:1.0'          | 'default'
        "sample:recommender:$version"     | 'interpolation' // verify GString doesn't cause issues
        'sample:recommender:1.0@pom'      | '@pom'
        "sample:recommender:$version@pom" | '@pom and interpolation'
    }

    @Unroll('when #bomLast overrides #bomFirst expect version #expectedVersion')
    def 'last bom overrides earlier boms'() {
        setup:
        def graph = new DependencyGraphBuilder()
                .addModule('example:foo:1.0.0')
                .addModule('example:foo:1.1.0')
                .build()
        def repo = projectDir.newFolder('repo')
        def generator = new GradleDependencyGenerator(graph, repo.path)

        generator.generateTestMavenRepo()

        def pom100 = new Pom('test', 'nebula-bom', '0.1.0', ArtifactType.POM)
        pom100.addManagementDependency('example', 'foo', '1.0.0')
        def pom100Dir = new File(repo, 'test/nebula-bom/0.1.0')
        pom100Dir.mkdirs()
        new File(pom100Dir, 'nebula-bom-0.1.0.pom').text = pom100.generate()
        def pom110 = new Pom('test', 'nebula-bom-snapshot', '0.1.0', ArtifactType.POM)
        pom110.addManagementDependency('example', 'foo', '1.1.0')
        def pom110Dir = new File(repo, 'test/nebula-bom-snapshot/0.1.0')
        pom110Dir.mkdirs()
        new File(pom110Dir, 'nebula-bom-snapshot-0.1.0.pom').text = pom110.generate()

        def project = ProjectBuilder.builder().build()
        project.apply plugin: 'java'
        project.repositories {
            maven { url repo }
        }

        project.dependencies {
            compile bomFirst
            compile bomLast
            compile 'example:foo'
        }

        def recommendations = new MavenBomRecommendationProvider(project)

        expect:
        recommendations.getVersion('example', 'foo') == expectedVersion

        where:
        bomFirst                         | bomLast                          | expectedVersion
        'test:nebula-bom:0.1.0'          | 'test:nebula-bom-snapshot:0.1.0' | '1.1.0'
        'test:nebula-bom-snapshot:0.1.0' | 'test:nebula-bom:0.1.0'          | '1.0.0'
    }
}
