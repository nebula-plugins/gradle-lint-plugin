package com.netflix.nebula.lint

import com.netflix.nebula.lint.rule.dependency.JavaFixture
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * Things that really belong in the unfinished Gradle Testkit
 */
abstract class TestKitSpecification extends Specification {
    @Rule final TemporaryFolder temp = new TemporaryFolder()
    File projectDir
    File buildFile
    List<File> pluginClasspath
    File settingsFile

    def setup() {
        projectDir = temp.root
        buildFile = new File(projectDir, 'build.gradle')
        settingsFile = new File(projectDir, 'settings.gradle')

        def pluginClasspathResource = getClass().classLoader.findResource('plugin-classpath.txt')
        if (pluginClasspathResource == null) {
            throw new IllegalStateException('Did not find plugin classpath resource, run `cCM` build task.')
        }

        pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }
    }

    def runTasksSuccessfully(String... tasks) {
        def result = GradleRunner.create()
                .withDebug(true)
                .withProjectDir(projectDir)
                .withArguments(*(tasks + '--stacktrace'))
                .withPluginClasspath(pluginClasspath)
                .build()

        tasks.each { task ->
            def outcome = result.task(":$task").outcome
            assert outcome == SUCCESS || outcome == UP_TO_DATE
        }

        result
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
