/**
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.nebula.lint.rule.wrapper

import com.netflix.nebula.lint.rule.test.AbstractRuleSpec
import org.gradle.util.GradleVersion
import spock.lang.Ignore

/**
 * @author Boaz Jan
 */
@Ignore("See issue #125")
class ArchaicWrapperRuleSpec extends AbstractRuleSpec {

    def 'wrapper without a configuration is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper)
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }

    def 'wrapper without gradleVersion in its configuration is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper) {
                archiveBase = PathBase.GRADLE_USER_HOME
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }

    def 'separately configured wrapper is not a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper)
            wrapper {
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 0
    }

    def 'wrapper task with gradleVersion twice configured is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper)
            wrapper {
               gradleVersion = '2.12'
            }
            wrapper {
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }


    def 'separately configured wrapper with a non standard task name is not a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrap(type: Wrapper)
            wrap {
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 0
    }

    def 'wrapper task with a non standard task name not configured is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrap(type: Wrapper)
            wrapper {
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }

    def 'wrapper defined with up to date version is not a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper) {
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 0
    }

    def 'wrapper task with multiple gradleVersion is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper) {
               gradleVersion = '2.12'
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }

    def 'wrapper task with gradleVersion in default configuration and separate configuration is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper) {
               gradleVersion = '2.12'
            }
            wrapper {
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }

    def 'wrapper defined with a version within the threshold is not a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.13')
        project.buildFile << """
            task wrapper(type: Wrapper) {
               gradleVersion = '2.11'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 0
    }

    def 'using a gradle executable which is older then the wrapper defined is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.0')
        project.buildFile << """
            task wrapper(type: Wrapper) {
               gradleVersion = '2.13'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }

    def 'wrapper defined with an old major version is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.0')
        project.buildFile << """
            task wrapper(type: Wrapper) {
               gradleVersion = '1.0'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }

    def 'wrapper defined with an old minor version is a violation'() {
        when:
        project = new WrapperProjectBuilder().createProject(canonicalName, ourProjectDir)
        project.gradle.version = GradleVersion.version('2.3')
        project.buildFile << """
            task wrapper(type: Wrapper) {
               gradleVersion = '2.0'
            }
        """

        def rule = new ArchaicWrapperRule()
        rule.project = project
        project.gradle.startParameter.setOffline(true)
        rule.majorThreshold = 0
        rule.minorThreshold = 2
        def results = runRulesAgainst(rule)

        then:
        results.violations.size() == 1
    }
}