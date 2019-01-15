/**
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

package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.TestKitSpecification


class SupportJavaLibrarySpec extends TestKitSpecification {
    def 'handles java-library plugin'() {
        given:
        setupWithJavaLibraryAndAllRules()

        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        !result.output.contains('This project contains lint violations.')
    }

    def 'verify rules are working - testing undeclared dependency'() {
        given:
        setupWithJavaLibraryAndAllRules(true)

        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        result.output.contains('undeclared-dependency')
        result.output.contains('This project contains lint violations.')
    }

    private def setupWithJavaLibraryAndAllRules(boolean undeclaredDependency = false) {
        definePluginOutsideOfPluginBlock = true

        def configuration
        if (undeclaredDependency) {
            configuration = 'implementation'
        } else {
            configuration = 'testCompile'
        }

        buildFile << """
        allprojects {
            apply plugin: 'nebula.lint'
            apply plugin: 'java-library'
            dependencies {
                ${configuration} 'junit:junit:4.11'
            }
            gradleLint.rules=['all-dependency']
            repositories {
                mavenCentral()
            }
        }
        """.stripIndent()

        addSubproject('sub1', """
            dependencies {
                api 'com.squareup.retrofit2:retrofit:2.4.0'
                implementation 'com.squareup.okhttp3:okhttp:3.10.0'
            }
            """.stripIndent())
        createJavaSourceFile(new File(projectDir.toString() + '/sub1'))
        createJavaTestFile(new File(projectDir.toString() + '/sub1'))
    }
}
