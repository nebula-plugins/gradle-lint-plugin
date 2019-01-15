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

package com.netflix.nebula.lint

import com.netflix.nebula.lint.rule.dependency.JavaFixture
import nebula.test.functional.internal.classpath.ClasspathAddingInitScriptBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GFileUtils
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

/**
 * Things that really belong in the unfinished Gradle Testkit
 */
abstract class TestKitSpecification extends Specification {
    @Rule
    final TestName testName = new TestName()

    File projectDir
    File buildFile
    File settingsFile
    File propertiesFile
    Boolean debug = true
    String moduleName
    String gradleVersion

    /**
     * Automatic addition of `GradleRunner.withPluginClasspath()` _only_ works if the plugin under test is applied using the plugins DSL
     * This enables us to add the plugin-under-test classpath via an init script
     * https://docs.gradle.org/4.6/userguide/test_kit.html#sub:test-kit-automatic-classpath-injection
     */
    boolean definePluginOutsideOfPluginBlock = false

    def setup() {
        projectDir = new File("build/nebulatest/${this.class.canonicalName}/${testName.methodName.replaceAll(/\W+/, '-')}").absoluteFile
        if (projectDir.exists()) {
            projectDir.deleteDir()
        }
        projectDir.mkdirs()

        buildFile = new File(projectDir, 'build.gradle')
        settingsFile = new File(projectDir, 'settings.gradle')
        propertiesFile = new File(projectDir, 'gradle.properties')

        moduleName = findModuleName()
        settingsFile.text = "rootProject.name='${moduleName}'\n"
    }

    def runTasksSuccessfully(String... tasks) {
        def initArgs = definePluginOutsideOfPluginBlock ? createGradleTestKitInitArgs() : new ArrayList<>()
        def gradleRunnerBuilder = GradleRunner.create()
                .withDebug(debug)
                .withProjectDir(projectDir)
                .withArguments(*(tasks + initArgs + '--stacktrace'))
                .withPluginClasspath()

        if (gradleVersion != null) {
            gradleRunnerBuilder.withGradleVersion(gradleVersion)
        }

        return gradleRunnerBuilder.build()
    }

    def runTasksFail(String... tasks) {
        def initArgs = definePluginOutsideOfPluginBlock ? createGradleTestKitInitArgs() : new ArrayList<>()
        def gradleRunnerBuilder = GradleRunner.create()
                .withDebug(debug)
                .withProjectDir(projectDir)
                .withArguments(*(tasks + initArgs))
                .withPluginClasspath()

        if (gradleVersion != null) {
            gradleRunnerBuilder.withGradleVersion(gradleVersion)
        }

        return gradleRunnerBuilder.buildAndFail()
    }

    @Deprecated
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

    def createJavaSourceFile(File projectDir) {
        createJavaSourceFile(projectDir, 'public class Main {}')
    }

    def createJavaTestFile(File projectDir, String source) {
        createJavaFile(projectDir, source, 'src/test/java')
    }

    def createJavaTestFile(String source) {
        createJavaTestFile(projectDir, source)
    }

    def createJavaTestFile(File projectDir) {
        createJavaTestFile(projectDir, '''
            import org.junit.Test;
            public class Test1 {
                @Test
                public void test() {}
            }
        '''.stripIndent())
    }

    def createJavaFile(File projectDir, String source, String sourceFolderPath) {
        def sourceFolder = new File(projectDir, sourceFolderPath)
        sourceFolder.mkdirs()
        new File(sourceFolder, JavaFixture.fullyQualifiedName(source).replaceAll(/\./, '/') + '.java').text = source
    }

    private String findModuleName() {
        getProjectDir().getName().replaceAll(/_\d+/, '')
    }

    private List<String> createGradleTestKitInitArgs() {
        File testKitDir = new File(projectDir, ".gradle-test-kit")
        if (!testKitDir.exists()) {
            GFileUtils.mkdirs(testKitDir)
        }

        File initScript = new File(testKitDir, "init.gradle")
        ClassLoader classLoader = this.getClass().getClassLoader()
        def classpathFilter = nebula.test.functional.GradleRunner.CLASSPATH_DEFAULT
        ClasspathAddingInitScriptBuilder.build(initScript, classLoader, classpathFilter)

        return Arrays.asList("--init-script", initScript.getAbsolutePath())
    }

}
