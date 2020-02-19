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

package com.netflix.nebula.lint.rule.dependency

import nebula.test.IntegrationTestKitSpec
import spock.lang.Issue
import spock.lang.Subject
import spock.lang.Unroll

@Subject(FirstOrderDuplicateDependencyClassRule)
class DuplicateDependencyClassRuleSpec extends IntegrationTestKitSpec {
    static def guava = 'com.google.guava:guava:18.0'
    static def collections = 'com.google.collections:google-collections:1.0'
    static def guava_transitive = 'com.netflix.nebula:gradle-metrics-plugin:4.1.6'
    static def asm = 'org.ow2.asm:asm:5.0.4'
    static def asm_asm = 'asm:asm:3.3.1'

    def setup() {
        debug = true
    }

    @Unroll
    def 'dependencies with duplicate classes cause violations'(List<String> deps, String message) {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                ${deps.collect { "implementation '$it'" }.join('\n')}
            }
        """

        when:
        writeJavaSourceFile('public class Main {}')
        def result = runTasks('compileJava')

        then:
        result.output.contains(message)
        result.output.contains("1 problem (0 errors, 1 warning)")

        where:
        deps                            | message
        [guava, collections]            | "$collections in configuration ':implementation' has 309 classes duplicated by $guava"
        [guava_transitive, collections] | "$collections in configuration ':implementation' has 309 classes duplicated by $guava"
        [asm, asm_asm]                  | "$asm_asm in configuration ':implementation' has 21 classes duplicated by $asm"
    }
    
    @Issue('47')
    def 'duplicate classes arising from different versions of the module selected in different configurations are not marked as duplicate'() {
        given:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                implementation 'log4j:log4j:1.2.16'
                testImplementation 'org.slf4j:slf4j-log4j12:1.7.7' // transitively depends on log4j:1.2.17
            }
        """

        when:
        writeHelloWorld()
        def result = runTasks('compileTestJava')

        then:
        !result.output.contains("log4j:log4j:1.2.16 in configuration 'compile'")
    }
    
    @Issue('42')
    def 'detect duplicate classes when dependency syntax is defined in an extension property'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']
            
            repositories { mavenCentral() }
            
            ext.deps = [
                guava: '$guava',
                collections: '$collections'
            ]
            
            dependencies {
                implementation deps.guava
                implementation deps.collections
            }
        """

        when:
        writeJavaSourceFile('public class Main{}')
        def result = runTasks('compileJava')

        then:
        result.output.contains("$collections in configuration ':implementation' has 309 classes duplicated by $guava")
    }

    @Issue('43')
    def 'duplicate dependency class warnings are ignorable'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']
            
            repositories { mavenCentral() }
            
            dependencies {
                gradleLint.ignore('duplicate-dependency-class') {
                    implementation '$guava'
                }
                implementation '$collections'
            }
        """

        when:
        writeJavaSourceFile('public class Main{}')
        def result = runTasks('compileJava')

        then:
        !result.output.contains("$collections in configuration ':implementation' has 309 classes duplicated by $guava")
    }

    /**
     * Fields defined on interfaces result in a generated class with the same name in every package that refers to them.  
     * In the below example, any reference to LOGGER generates a class called org/hornetq/utils/HornetQUtilLogger_$logger
     * in the referring package.
     * 
     * public interface HornetQUtilLogger extends BasicLogger {
     *      HornetQUtilLogger LOGGER = Logger.getMessageLogger(...);
     * }
     */
    @Issue('52')
    def 'disregard classes generated by reference to interface fields'() {
        when:
        buildFile.text = """
            plugins {
                id 'nebula.lint'
                id 'java'
            }

            gradleLint {
                rules = ['duplicate-dependency-class']
            }

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.hornetq:hornetq-core-client:2.4.5.Final'
                implementation 'org.hornetq:hornetq-commons:2.4.5.Final'
            }
        """

        writeJavaSourceFile('public class A { }')
        
        then:
        def results = runTasks('compileJava')
        !results.output.contains('duplicate-dependency-class')
    }

    @Issue('#67')
    def 'ignore dependencies that are provided by extension properties'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            ext.deps = [ guava: '$guava' ]

            dependencies {
                gradleLint.ignore('duplicate-dependency-class') {
                    implementation deps.guava
                }
                implementation '$collections'
            }
        """

        when:
        writeJavaSourceFile('public class Main{}')
        def result = runTasks('compileJava')

        then:
        !result.output.contains("$collections in configuration ':implementation' has 309 classes duplicated by $guava")
    }

    def 'duplicate classes between transitive dependencies do not cause violations'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                implementation 'com.google.guava:guava-gwt:10.0.1' // guava transitive
                implementation 'org.jvnet.hudson.plugins:maven-dependency-update-trigger:1.2' // google-collections transitive
            }
        """

        when:
        writeJavaSourceFile('public class Main{}')
        def result = runTasks('compileJava', 'lintGradle')

        then:
        !result.output.contains("com.google.collections:google-collections:1.0 in configuration ':implementation' has 384 classes duplicated by com.google.guava:guava:10.0.1")
    }

    @Issue('#139')
    def 'duplicate module/package classes do not cause violations'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                implementation 'org.slf4j:slf4j-api:1.8.0-alpha2'
                testRuntime 'org.slf4j:slf4j-simple:1.8.0-alpha2'
            }
        """

        expect:
        writeJavaSourceFile('public class Main{}')
        def result = runTasks('compileJava', 'lintGradle')
    }

    @Issue('#98')
    def 'duplicate classes between transitive dependencies cause violations when the transitive rule is used'() {
        setup:
        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['transitive-duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                implementation 'com.google.guava:guava-gwt:10.0.1' // guava transitive
                implementation 'org.jvnet.hudson.plugins:maven-dependency-update-trigger:1.2' // google-collections transitive
            }
        """

        when:
        writeJavaSourceFile('public class Main{}')
        def result = runTasks('compileJava', '--info')

        then:
        result.output.contains("com.google.collections:google-collections:1.0 in configuration ':implementation' has 384 classes duplicated by com.google.guava:guava:10.0.1")
    }

    @Issue('#246')
    def 'duplicate META-INF/versions/9/module-info do not cause violations'() {
        debug = true

        buildFile.text = """
            plugins {
                id 'java'
                id 'nebula.lint'
            }

            gradleLint.rules = ['duplicate-dependency-class', 'transitive-duplicate-dependency-class']

            repositories { mavenCentral() }

            dependencies {
                // these dependencies both have META-INF/versions/9/module-info
                implementation 'io.github.classgraph:classgraph:4.8.45'
                implementation 'org.apache.logging.log4j:log4j-api:2.12.0'
            }
            """.stripIndent()

        when:
        writeJavaSourceFile('public class Main{}')
        def result = runTasks('compileJava', 'lintGradle')

        then:
        !result.output.contains("io.github.classgraph:classgraph:4.8.45 in configuration ':implementation' has 1 classes duplicated by org.apache.logging.log4j:log4j-api:2.12.0")
        !result.output.contains("This project contains lint violations")
    }
}
