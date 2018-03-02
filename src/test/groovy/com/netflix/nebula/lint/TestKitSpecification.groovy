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

package com.netflix.nebula.lint

import com.netflix.nebula.lint.rule.dependency.JavaFixture
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Things that really belong in the unfinished Gradle Testkit
 */
abstract class TestKitSpecification extends Specification {
    @Rule final TemporaryFolder temp = new TemporaryFolder()
    File projectDir
    File buildFile
    File settingsFile
    File propertiesFile
    Boolean debug = true

    def setup() {
        projectDir = temp.root
        buildFile = new File(projectDir, 'build.gradle')
        settingsFile = new File(projectDir, 'settings.gradle')
        propertiesFile = new File(projectDir, 'gradle.properties')
    }

    def runTasksSuccessfully(String... tasks) {
        return GradleRunner.create()
                .withDebug(debug)
                .withProjectDir(projectDir)
                .withArguments(*(tasks + '--stacktrace'))
                .withPluginClasspath()
                .build()
    }

    def runTasksFail(String... tasks) {
        return GradleRunner.create()
                .withDebug(debug)
                .withProjectDir(projectDir)
                .withArguments(*(tasks))
                .withPluginClasspath()
                .buildAndFail()
    }

    def runTasksSuccessfullyWithGradleVersion(String gradleVersion, String... tasks) {
        return GradleRunner.create()
                .withDebug(debug)
                .withProjectDir(projectDir)
                .withArguments(*(tasks + '--stacktrace'))
                .withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .build()
    }

    File addSubproject(String name) {
        def subprojectDir = new File(projectDir, name)
        subprojectDir.mkdirs()
        settingsFile << "include '$name'\n"
        return subprojectDir
    }

    File addSubproject(String name, String buildGradleContents) {
        def subprojectDir = new File(projectDir, name)
        subprojectDir.mkdirs()
        new File(subprojectDir, 'build.gradle').text = buildGradleContents
        settingsFile << "include '$name'\n"
        return subprojectDir
    }

    def dependencies(File _buildFile, String... confs = ['compile', 'testCompile']) {
        _buildFile.text.readLines()
                .collect { it.trim() }
                .findAll { line -> confs.any { c -> line.startsWith(c) } }
                .collect { it.split(/\s+/)[1].replaceAll(/['"]/, '') }
                .sort()
    }

    def createJavaSourceFile(String source) {
        createJavaSourceFile(projectDir, source)
    }

    def createJavaSourceFile(File projectDir, String source) {
        createJavaFile(projectDir, source, 'src/main/java')
    }

    def createJavaTestFile(File projectDir, String source) {
        createJavaFile(projectDir, source, 'src/test/java')
    }

    def createJavaTestFile(String source) {
        createJavaTestFile(projectDir, source)
    }

    def createJavaFile(File projectDir, String source, String sourceFolderPath) {
        def sourceFolder = new File(projectDir, sourceFolderPath)
        sourceFolder.mkdirs()
        new File(sourceFolder, JavaFixture.fullyQualifiedName(source).replaceAll(/\./, '/') + '.java').text = source
    }
}
