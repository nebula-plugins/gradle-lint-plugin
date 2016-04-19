package com.netflix.nebula.lint

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

/**
 * Things that really belong in the unfinished Gradle Testkit
 */
class TestKitSpecification extends Specification {
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
                .withArguments(tasks)
                .withPluginClasspath(pluginClasspath)
                .build()

        tasks.each { task ->
            def outcome = result.task(":$task").outcome
            assert outcome == SUCCESS || outcome == UP_TO_DATE
        }

        println result.output

        result
    }

    def addSubproject(String name) {
        new File(projectDir, name).mkdirs()
        settingsFile << "include '$name'\n"
    }

    def addSubproject(String name, String buildGradleContents) {
        def subprojectDir = new File(projectDir, name)
        subprojectDir.mkdirs()
        new File(subprojectDir, 'build.gradle').text = buildGradleContents
        settingsFile << "include '$name'\n"
    }
}
