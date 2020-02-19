/*
 * Copyright 2015-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.nebula.lint.plugin


import nebula.test.IntegrationTestKitSpec
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Unroll

class GradleLintPluginSpec extends IntegrationTestKitSpec {
    def setup() {
        debug = true
    }

    def 'run multiple rules on a single module project'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                implementation('com.google.guava:guava:18.0')
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasks('assemble')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
    }

    def 'run multiple rules on a single module project with applied file'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            
            dependencies {
                implementation('commons-lang:commons-lang:2.6')
            }

            apply from: 'dependencies.gradle'
        """
        File dependenciesFile = new File(projectDir, 'dependencies.gradle')
        dependenciesFile.text = """
            dependencies {
                implementation('com.google.guava:guava:18.0')
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasks('assemble')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 3
        console.findAll { it.contains('dependency-parentheses') }.size() == 2
        console.any { it.contains('dependency-tuple') }
    }



    def 'run rules on multi-module project where one of the subprojects has no build.gradle'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
            }

            subprojects {
                apply plugin: 'nebula.lint'
                apply plugin: 'java'
            }

            allprojects {
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }
        """

        addSubproject('sub')

        then:
        runTasks('lintGradle')
    }

    def 'run rules on multi-module project where one of the subprojects does not apply gradle lint'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
            }

            subprojects {
                apply plugin: 'java'
                apply plugin: 'nebula.lint'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
        """

        addSubproject('sub', """
            dependencies {
                implementation('a:a:1')
            }
        """)

        then:
        runTasks('lintGradle')
    }

    def 'run rules on multi-module only once'() {
        setup:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    implementation('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        addSubproject('sub2', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskB {}
        """)

        when:
        def result = runTasks("assemble")

        then:
        result.output.readLines().findAll { it.contains('warning   dependency-tuple')}.size() == 2
        result.output.readLines().findAll { it.contains('warning   dependency-parentheses')}.size() == 1
    }

    def 'run rules on multi-module only once when task fails'() {
        setup:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    implementation('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task hello {
                doLast {
                  throw new RuntimeException("test")
                }
            }
        """)

        addSubproject('sub2', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task hello {
                doLast {
                  println "hello"
                }
            }
        """)

        when:
        def result = runTasksAndFail("hello")

        then:
        result.output.readLines().findAll { it.contains('warning   dependency-tuple')}.size() == 2
        result.output.readLines().findAll { it.contains('warning   dependency-parentheses')}.size() == 1
    }

    def 'run rules on multi-module only once when task fails - parallel'() {
        setup:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    implementation('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task hello {
                doLast {
                  throw new RuntimeException("test")
                }
            }
        """)

        addSubproject('sub2', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task hello {
                doLast {
                  throw new RuntimeException("test")
                }
            }
        """)

        addSubproject('sub3', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task hello {
                doLast {
                  throw new RuntimeException("test")
                }
            }
        """)


        when:
        def result = runTasksAndFail("hello", "--parallel")

        then:
        result.output.readLines().findAll { it.contains('warning   dependency-tuple')}.size() == 3
        result.output.readLines().findAll { it.contains('warning   dependency-parentheses')}.size() == 1
    }


    def 'run rules on multi-module only once in parallel'() {
        setup:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    implementation('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        addSubproject('sub2', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskB {}
        """)

        addSubproject('sub3', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskB {}
        """)

        when:
        def result = runTasks("assemble", "--parallel")

        then:
        result.output.readLines().findAll { it.contains('warning   dependency-tuple')}.size() == 3
        result.output.readLines().findAll { it.contains('warning   dependency-parentheses')}.size() == 1
    }


    def 'run only critical rules and skip normal ones'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses']
            gradleLint.criticalRules = ['dependency-tuple']

            dependencies {
                implementation('com.google.guava:guava:18.0')
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasksAndFail('criticalLintGradle')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('error') }.size() == 1
        console.any { it.contains('dependency-tuple') }
        console.every { ! it.contains('dependency-parentheses') }
    }

    @Unroll
    def 'auto correct all violations on a single module project with task #taskName'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                implementation('com.google.guava:guava:18.0')
            }
        """

        then:
        runTasks(taskName)

        buildFile.text == """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                implementation 'com.google.guava:guava:18.0'
            }
        """.toString()

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    @Unroll
    def 'auto correct all violations on a multi-module project with task #taskName'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                dependencies {
                    implementation('com.google.guava:guava:18.0')
                }
            }
        """

        def subDir = addSubproject('sub', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        then:
        runTasks(taskName)

        buildFile.text.contains("implementation 'com.google.guava:guava:18.0'")
        String buildFileText = new File(subDir, 'build.gradle').text
        buildFileText.contains("testImplementation")
        buildFileText.contains("junit:junit:4.11")

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    @Unroll
    def 'auto correct violations on a multi-module project where one of the subprojects does not apply gradle lint with task #taskName'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
            }
            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
        """

        addSubproject('sub', """
            apply plugin: 'java'
            apply plugin: 'nebula.lint'
            
            dependencies {
                implementation('a:a:1')
            }
        """)

        then:
        runTasks(taskName)

        where:
        taskName << ['fixGradleLint', 'fixLintGradle']
    }

    def 'rules relative to each project'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
            }

            allprojects {
                apply plugin: 'nebula.lint'
                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }

            subprojects {
                apply plugin: 'java'
                
                dependencies {
                    implementation('com.google.guava:guava:18.0')
                }
            }
        """

        addSubproject('sub', """
            dependencies {
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }

            task taskA {}
        """)

        then:
        def results = runTasks('taskA')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
    }

    def 'generate a lint report for a single module project'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']

            dependencies {
                implementation('com.google.guava:guava:18.0')
                testImplementation group: 'junit',
                    name: 'junit',
                    version: '4.11'
            }
        """

        then:
        def results = runTasks('generateGradleLintReport')

        when:
        def console = results.output.readLines()

        then:
        console.findAll { it.startsWith('warning') }.size() == 2
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('dependency-tuple') }
        new File(projectDir, "build/reports/gradleLint/${projectDir.name}.html").exists()
    }


    def 'test wrapper rule on a single module project'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint.rules = ['dependency-parentheses']
            dependencies {
                implementation('commons-logging:commons-logging:latest.release')
            }
        """

        then:
        def results = runTasks('autoLintGradle')

        when:
        def console = results.output.readLines()

        then:
        console.any { it.contains('dependency-parentheses') }
    }

    def 'build fails for violations in manual lint'() {
        given:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint.rules = ['dependency-parentheses']
            dependencies {
                implementation('commons-logging:commons-logging:latest.release')
            }
        """

        when:
        def results = runTasksAndFail('lintGradle')

        then:
        def console = results.output.readLines()
        console.any { it.contains('dependency-parentheses') }
        console.any { it.contains('This build contains 1 lint violation') }
    }

    @Issue('#68')
    def 'lint task does not run when alwaysRun is off'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint { 
                rules = ['dependency-parentheses']
                alwaysRun = false
            }
            dependencies {
                implementation('commons-logging:commons-logging:latest.release')
            }
        """

        then:
        // build would normally trigger lintGradle, but will not when alwaysRun = false
        def results = runTasks('build')

        when:
        def console = results.output.readLines()

        then:
        !console.any { it.contains('dependency-parentheses') }
    }

    def 'lint task does not run when alwaysRun is off via cli'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint.rules = ['dependency-parentheses']
            dependencies {
                implementation('commons-logging:commons-logging:latest.release')
            }
        """

        then:
        // build would normally trigger lintGradle, but will not when alwaysRun = false
        def results = runTasks('build', '-PgradleLint.alwaysRun=false')

        when:
        def console = results.output.readLines()

        then:
        !console.any { it.contains('dependency-parentheses') }
    }

    def 'lint task does not run when autoGradleLint is excluded via cli'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint.rules = ['dependency-parentheses']
            dependencies {
                implementation('commons-logging:commons-logging:latest.release')
            }
        """

        then:
        // build would normally trigger lintGradle, but will not when alwaysRun = false
        def results = runTasks('build', '-x', 'autoLintGradle')

        when:
        def console = results.output.readLines()

        then:
        !console.any { it.contains('dependency-parentheses') }
    }

    @Unroll
    def 'lint task does not run for task #taskName'() {
        when:
        buildFile << """
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint.rules = ['dependency-parentheses']
            dependencies {
                implementation('commons-logging:commons-logging:latest.release')
            }
            task helloTask {
                println 'hello'
            } 
        """

        then:
        // build would normally trigger lintGradle, but will not when alwaysRun = false
        def results = runTasks(taskName)

        when:
        def console = results.output.readLines()

        then:
        !console.any { it.contains('dependency-parentheses') }

        where:
        taskName << ['help', 'tasks', 'dependencies', 'components',
                     'model', 'projects', 'properties', 'wrapper']
    }

    def 'autoLintGradle is always run'() {
        setup:
        buildFile << """\
            plugins {
                id 'nebula.lint'
                id 'java'
            }
            gradleLint.rules = ['dependency-parentheses']
            dependencies {
                implementation('commons-logging:commons-logging:latest.release')
            }            
            """.stripIndent()

        when:
        def results = runTasks('compileJava')

        then:
        results.output.contains('This project contains lint violations.')
        results.output.contains('dependency-parentheses')
    }

    def 'override rule set with a gradle property'() {
        writeHelloWorld()
        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation('com.google.guava:guava:21.0')
            }
            """.stripIndent()

        when:
        def results = runTasks('fixGradleLint',
                '-PgradleLint.rules=dependency-parentheses')

        then:
        results.task(':fixGradleLint').outcome == TaskOutcome.SUCCESS
        buildFile.text.contains("implementation 'com.google.guava:guava:21.0'")
    }

    def 'exclude individual rules with a gradle property or gradleLint.excludedRules'() {
        when:
        buildFile << """\
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            
            gradleLint.criticalRules = ['all-dependency', 'dependency-parentheses']
            
            // realistically, we wouldn't exclude something we just directly included...
            gradleLint.excludedRules = ['dependency-parentheses']
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation('com.google.guava:guava:21.0')
            }
            """.stripIndent()

        writeHelloWorld()

        then:
        runTasks('lintGradle', '-PgradleLint.excludedRules=unused-dependency')
    }

    @Issue('#100')
    def 'empty build file lints successfully'() {
        when:
        buildFile << """\
            plugins {
                id 'nebula.lint'
            }

            subprojects {
                apply plugin: 'nebula.lint'
                apply plugin: 'java'

                gradleLint.rules = ['dependency-parentheses', 'dependency-tuple']
            }
            """.stripIndent()

        new File(addSubproject('sub1'), 'build.gradle').createNewFile()

        then:
        runTasks('fixGradleLint')
    }

    def 'lint plugin cannot be applied to kotlin script build files'() {
        given:
        buildFile = new File(projectDir, 'build.gradle.kts')
        buildFile << """
        plugins {
            id("nebula.lint")
        }
        """

        when:
        def failure = runTasksAndFail("clean")

        then:
        failure.output.contains("Gradle Lint Plugin currently doesn't support kotlin build scripts." +
              " Please, switch to groovy build script if you want to use linting.")
    }
}
