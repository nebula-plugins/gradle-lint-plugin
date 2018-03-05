/*
 *
 *  Copyright 2018 Netflix, Inc.
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

import nebula.test.IntegrationSpec
import spock.lang.Unroll

class RecommendedVersionsRuleSpec extends IntegrationSpec {
    private static final String V_4_POINT_1 = '4.1'
    private static final String V_4_POINT_5 = '4.5'
    private static final String V_4_POINT_6 = '4.6'
    private static final String SUBPROJECTS = 'subprojects'
    private static final String ALLPROJECTS = 'allprojects'

    def setup() {
        if (settingsFile.exists()) {
            settingsFile.delete()
        }
        def propertiesFile = new File("gradle.properties")
        if (propertiesFile.exists()) {
            propertiesFile.delete()
        }
    }

    @Unroll
    def 'v#versionOfGradle - base - remove version from dependency when bom has version - #expectVersionsRemoved'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile.text = """
            buildscript {  repositories { jcenter() } }
            repositories { maven { url "${repo}" } }

            apply plugin: 'java'
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['recommended-versions']

            dependencies {
                compile 'sample:recommender:1.0'
                compile 'commons-logging:commons-logging:latest.release'
            }
        """.stripIndent()
        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:
        if (expectVersionsRemoved) {
            assertDependenciesHaveVersionsRemoved(buildFile, 'commons-logging:commons-logging')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(buildFile, 'commons-logging:commons-logging')
        }

        where:
        versionOfGradle | lowerVersionOfGradle | expectVersionsRemoved
        V_4_POINT_1     | true                 | false
        V_4_POINT_5     | false                | true
        V_4_POINT_6     | false                | true
    }

    @Unroll
    def 'v#versionOfGradle - base - preserve versions when bom does not contain version'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile.text = """
            buildscript {  repositories { jcenter() } }
            repositories { maven { url "${repo}" } }

            apply plugin: 'java'
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['recommended-versions']

            dependencies {
                compile 'sample:recommender:1.0'
                compile 'com.google.guava:guava:19.0'
            }
        """.stripIndent()
        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:
        assertDependenciesPreserveVersions(buildFile, 'com.google.guava:guava')

        where:
        versionOfGradle | lowerVersionOfGradle
        V_4_POINT_1     | true
        V_4_POINT_5     | false
        V_4_POINT_6     | false
    }

    @Unroll
    def 'v#versionOfGradle - base - remove version from dependency when bom has version set via property - #expectVersionsRemoved'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile.text = """
            buildscript {  repositories { jcenter() } }
            repositories { maven { url "${repo}" } }

            apply plugin: 'java'
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['recommended-versions']
            
            ext {
                commonsVersion = '1.1.2'
            }

            dependencies {
                compile 'sample:recommender:1.0'
                compile 'commons-lang:commons-lang:latest.release'
                compile 'commons-logging:commons-logging:latest.release'
            }
        """.stripIndent()
        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:
        if (expectVersionsRemoved) {
            assertDependenciesHaveVersionsRemoved(buildFile, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(buildFile, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
        }

        where:
        versionOfGradle | lowerVersionOfGradle | expectVersionsRemoved
        V_4_POINT_1     | true                 | false
        V_4_POINT_5     | false                | true
        V_4_POINT_6     | false                | true
    }

    @Unroll
    def 'v#versionOfGradle, #subOrAllProjects: remove version from dependency - #expectVersionsRemoved'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile << '''
            buildscript {  repositories { jcenter() } }
            '''.stripIndent()

        if (subOrAllProjects == SUBPROJECTS) {
            buildFile << '''\
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                '''.stripIndent()
        }

        buildFile << """\
            ${subOrAllProjects} {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                
                gradleLint.rules = ['recommended-versions']
                
                repositories { maven { url "${repo}" } }
            }
        """.stripIndent()

        addSubproject('sub1', """\
            dependencies {
                compile 'sample:recommender:1.0'
                compile 'commons-logging:commons-logging:latest.release'
            }
            """.stripIndent())

        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:
        def sub1Gradle = new File(projectDir, 'sub1/build.gradle')
        if (expectVersionsRemoved) {
            assertDependenciesHaveVersionsRemoved(sub1Gradle, 'commons-logging:commons-logging')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(sub1Gradle, 'commons-logging:commons-logging')
        }

        where:
        versionOfGradle | lowerVersionOfGradle | expectVersionsRemoved | subOrAllProjects
        V_4_POINT_1     | true                 | false                 | SUBPROJECTS
        V_4_POINT_1     | true                 | false                 | ALLPROJECTS
        V_4_POINT_5     | false                | true                  | SUBPROJECTS
        V_4_POINT_5     | false                | true                  | ALLPROJECTS
        V_4_POINT_6     | false                | true                  | SUBPROJECTS
        V_4_POINT_6     | false                | true                  | ALLPROJECTS
    }

    @Unroll
    def 'v#versionOfGradle, #subOrAllProjects - remove version from dependency when bom has version set via property - #expectVersionsRemoved'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile << """
            buildscript {  repositories { jcenter() } }
            ext {
                commonsVersion = '1.1.2'
            }
            """.stripIndent()

        if (subOrAllProjects == SUBPROJECTS) {
            buildFile << '''\
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                '''.stripIndent()
        }

        buildFile << """\
            ${subOrAllProjects} {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                
                gradleLint.rules = ['recommended-versions']
                
                repositories { maven { url "${repo}" } }
            }
            """.stripIndent()

        addSubproject('sub1', """\
            dependencies {
                compile 'sample:recommender:1.0'
                compile 'commons-lang:commons-lang:latest.release'
                compile 'commons-logging:commons-logging:latest.release'
            }
            """.stripIndent())

        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:
        def sub1Gradle = new File(projectDir, 'sub1/build.gradle')
        if (expectVersionsRemoved) {
            assertDependenciesHaveVersionsRemoved(sub1Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(sub1Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
        }

        where:
        versionOfGradle | lowerVersionOfGradle | expectVersionsRemoved | subOrAllProjects
        V_4_POINT_1     | true                 | false                 | SUBPROJECTS
        V_4_POINT_1     | true                 | false                 | ALLPROJECTS
        V_4_POINT_5     | false                | true                  | SUBPROJECTS
        V_4_POINT_5     | false                | true                  | ALLPROJECTS
        V_4_POINT_6     | false                | true                  | SUBPROJECTS
        V_4_POINT_6     | false                | true                  | ALLPROJECTS
    }

    @Unroll
    def 'v#versionOfGradle, #subOrAllProjects, multiple subprojects - remove version from dependency - #expectVersionsRemoved'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile << """
            buildscript {  repositories { jcenter() } }
            ext {
                commonsVersion = '1.1.2'
            }
            """.stripIndent()

        if (subOrAllProjects == SUBPROJECTS) {
            buildFile << '''\
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                '''.stripIndent()
        }

        buildFile << """\
            ${subOrAllProjects} {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                
                gradleLint.rules = ['recommended-versions']
                
                repositories { maven { url "${repo}" } }
            }
            """.stripIndent()

        addSubproject('sub1', """\
            dependencies {
                compile 'sample:recommender:1.0'
                compile 'commons-lang:commons-lang:latest.release'
                compile 'commons-logging:commons-logging:latest.release'
            }
            """.stripIndent())

        addSubproject('sub2', """\
            dependencies {
                compile 'sample:recommender:1.0'
                compile 'commons-lang:commons-lang:latest.release'
                compile 'commons-logging:commons-logging:latest.release'
            }
            """.stripIndent())

        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:
        def sub1Gradle = new File(projectDir, 'sub1/build.gradle')
        def sub2Gradle = new File(projectDir, 'sub2/build.gradle')
        if (expectVersionsRemoved) {
            assertDependenciesHaveVersionsRemoved(sub1Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
            assertDependenciesHaveVersionsRemoved(sub2Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(sub1Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
            assertDependenciesPreserveVersions(sub2Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
        }

        where:
        versionOfGradle | lowerVersionOfGradle | expectVersionsRemoved | subOrAllProjects
        V_4_POINT_1     | true                 | false                 | SUBPROJECTS
        V_4_POINT_1     | true                 | false                 | ALLPROJECTS
        V_4_POINT_5     | false                | true                  | SUBPROJECTS
        V_4_POINT_5     | false                | true                  | ALLPROJECTS
        V_4_POINT_6     | false                | true                  | SUBPROJECTS
        V_4_POINT_6     | false                | true                  | ALLPROJECTS
    }

    @Unroll
    def 'v#versionOfGradle, #subOrAllProjects - preserve versions when bom does not contain version'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile.text = """
            buildscript {  repositories { jcenter() } }
            ext {
                commonsVersion = '1.1.2'
            }
            """.stripIndent()

        if (subOrAllProjects == SUBPROJECTS) {
            buildFile << '''\
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                '''.stripIndent()
        }

        buildFile << """\
            ${subOrAllProjects} {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                
                gradleLint.rules = ['recommended-versions']
                
                repositories { maven { url "${repo}" } }
            }
            """.stripIndent()

        addSubproject('sub1', """\
            dependencies {
                compile 'sample:recommender:1.0'
                compile 'com.google.guava:guava:19.0'
                compile 'commons-lang:commons-lang:latest.release'
                compile 'commons-logging:commons-logging:latest.release'
            }
            """.stripIndent())

        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:

        def sub1Gradle = new File(projectDir, 'sub1/build.gradle')
        assertDependenciesPreserveVersions(sub1Gradle, 'com.google.guava:guava')

        if (expectRecommendedVersionsRemoved) {
            assertDependenciesHaveVersionsRemoved(sub1Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(sub1Gradle, 'commons-lang:commons-lang', 'commons-logging:commons-logging')
        }

        where:
        versionOfGradle | lowerVersionOfGradle | expectRecommendedVersionsRemoved | subOrAllProjects
        V_4_POINT_1     | true                 | false                            | SUBPROJECTS
        V_4_POINT_1     | true                 | false                            | ALLPROJECTS
        V_4_POINT_5     | false                | true                             | SUBPROJECTS
        V_4_POINT_5     | false                | true                             | ALLPROJECTS
        V_4_POINT_6     | false                | true                             | SUBPROJECTS
        V_4_POINT_6     | false                | true                             | ALLPROJECTS
    }

    @Unroll
    def 'v#versionOfGradle, #subOrAllProjects - multiproject sets platform bom given all config in root project'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile << """
            buildscript {  repositories { jcenter() } }
            ext {
                commonsVersion = '1.1.2'
            }
            allprojects {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['recommended-versions']
                repositories { maven { url "${repo}" } }
                dependencies {
                    compile 'sample:recommender:1.0'
                }
            }
            dependencies {
                compile 'commons-configuration:commons-configuration:latest.release'
            }
            ${subOrAllProjects} {
                dependencies {
                    compile 'commons-lang:commons-lang:latest.release'
                    compile 'commons-logging:commons-logging:latest.release'
                }
            }
            """.stripIndent()

        setupGradleVersion(versionOfGradle)
        setupSettingsFile()
        setupPropertiesFile()

        when:
        def result = runTasks('fixGradleLint')

        then:
        if (expectVersionsRemoved) {
            assertDependenciesHaveVersionsRemoved(buildFile,
                    'commons-lang:commons-lang',
                    'commons-logging:commons-logging',
                    'commons-configuration:commons-configuration')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(buildFile,
                    'commons-lang:commons-lang',
                    'commons-logging:commons-logging',
                    'commons-configuration:commons-configuration')
        }

        where:
        versionOfGradle | lowerVersionOfGradle | expectVersionsRemoved | subOrAllProjects
        V_4_POINT_1     | true                 | false                 | SUBPROJECTS
        V_4_POINT_1     | true                 | false                 | ALLPROJECTS
//        V_4_POINT_5     | false                | true                  | SUBPROJECTS  // FIXME: why doesn't this work?
        V_4_POINT_5     | false                | true                  | ALLPROJECTS
//        V_4_POINT_6     | false                | true                  | SUBPROJECTS
        V_4_POINT_6     | false                | true                  | ALLPROJECTS
    }

    @Unroll
    def 'v#versionOfGradle - runs (#shouldRemoveDependencyVersions) with setup: prop - #addedProperties, settings - #addedSettings'() {
        given:
        setup()
        def repo = new File(projectDir, 'repo')
        repo.mkdirs()
        setupSampleBomFile(repo, 'recommender')

        buildFile.text = """
            buildscript {  repositories { jcenter() } }
            repositories { maven { url "${repo}" } }

            apply plugin: 'java'
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['recommended-versions']

            dependencies {
                compile 'sample:recommender:1.0'
                compile 'commons-logging:commons-logging:latest.release'
            }
        """.stripIndent()
        setupGradleVersion(versionOfGradle)
        if (addedProperties) {
            setupPropertiesFile()
        }
        if (addedSettings) {
            setupSettingsFile()
        }

        when:
        def result = runTasks('fixGradleLint')

        then:
        if (shouldRemoveDependencyVersions) {
            assertDependenciesHaveVersionsRemoved(buildFile, 'commons-logging:commons-logging')
            result.standardOutput.contains('fixed          recommended-dependency')
        } else {
            assertDependenciesPreserveVersions(buildFile, 'commons-logging:commons-logging')
        }

        where:
        versionOfGradle | addedProperties | addedSettings | shouldRemoveDependencyVersions
        V_4_POINT_5     | true            | true          | true
        V_4_POINT_1     | true            | true          | false
        V_4_POINT_5     | false           | true          | false
        V_4_POINT_5     | true            | false         | false

        V_4_POINT_6     | false           | true          | true    // doesn't need properties set
        V_4_POINT_6     | true            | true          | true
    }


    @Unroll
    def 'v#versionOfGradle - there are no problems with a basic configuration, without settings or properties'() {
        given:
        setup()
        buildFile.text = """
            buildscript {  repositories { jcenter() } }

            apply plugin: 'java'
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['recommended-versions']

            dependencies {
                compile 'commons-logging:commons-logging:latest.release'
            }
        """.stripIndent()
        setupGradleVersion(versionOfGradle)

        when:
        def result = runTasks('fixGradleLint')

        then:
        assertDependenciesPreserveVersions(buildFile, 'commons-logging:commons-logging')
        !result.standardOutput.contains('fixed          recommended-dependency')
        !result.standardOutput.contains('Exception')
        !result.standardOutput.contains('FileNotFoundException')

        def filesAsString = projectDir.listFiles().toString()
        filesAsString.contains(projectDir.toString() + File.separator + "build.gradle")
        !filesAsString.contains(projectDir.toString() + File.separator + "gradle.properties")
        !filesAsString.contains(projectDir.toString() + File.separator + "settings.gradle")

        where:
        versionOfGradle << [V_4_POINT_1, V_4_POINT_5, V_4_POINT_6]
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
                    <groupId>commons-lang</groupId>
                    <artifactId>commons-lang</artifactId>
                    <version>\${commonsVersion}</version>
                  </dependency>
                  <dependency>
                    <groupId>commons-configuration</groupId>
                    <artifactId>commons-configuration</artifactId>
                    <version>1.1.2</version>
                  </dependency>
                </dependencies>
              </dependencyManagement>
            </project>
        """.stripIndent()
        setupSampleFileWith(repo, artifactName, sampleFileContents)
    }

    private static File setupSampleFileWith(File repo, String artifactName, String sampleFileContents) {
        String repoPath = File.separator + 'sample' + File.separator + artifactName + File.separator + '1.0'
        def sample = new File(repo, repoPath)
        sample.mkdirs()
        def sampleFile = new File(sample, artifactName + '-1.0.pom')
        sampleFile << sampleFileContents
    }

    private void setupGradleVersion(String versionOfGradle) {
        gradleVersion = versionOfGradle
    }

    private void setupSettingsFile() {
        def settingsFile = new File(projectDir, "settings.gradle")

        FeaturePreviewsFixture.enableExperimentalFeatures(settingsFile, gradleVersion)
        FeaturePreviewsFixture.enableImprovedPomSupport(settingsFile, gradleVersion)
    }

    private void setupPropertiesFile() {
        def propertiesFile = new File(projectDir, "gradle.properties")

        FeaturePreviewsFixture.enableImprovedPomSupport(propertiesFile, gradleVersion)
    }

    private static void assertDependenciesPreserveVersions(File buildGradle, String... deps) {
        deps.each {
            assert buildGradle.text.find(/compile '${it}:.+'/)
        }
    }

    private static void assertDependenciesHaveVersionsRemoved(File buildGradle, String... deps) {
        deps.each {
            assert buildGradle.text.find(/compile '${it}'/)
            assert !buildGradle.text.find(/compile '${it}:.+'/)
        }
    }
}