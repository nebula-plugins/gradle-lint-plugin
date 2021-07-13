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

package com.netflix.nebula.lint.rule.dependency

/**
 * This is based on Gradle's internal testing. Investigate here once these features are no longer under preview.
 *
 * - gradle/subprojects/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/FeaturePreviewsFixture.groovy
 * @param settings file
 * @param properties file
 */
class FeaturePreviewsFixture {
    public static final String GRADLE_VERSION_WITH_EXPERIMENTAL_FEATURES = '4.5'
    public static final String GRADLE_VERSION_WITH_OPT_IN_FEATURES = '4.6'
    private static final String GRADLE_VERSION_WITH_DEFAULT_FEATURES = '5.0'
    public static final String GRADLE_PROPERTIES_FILE = 'gradle.properties'
    public static final String GRADLE_SETTINGS_FILE = 'settings.gradle'

    static void enableImprovedPomSupport(File file, String gradleVersion) {
        def fileName = file.getName()
        if (gradleVersion < GRADLE_VERSION_WITH_EXPERIMENTAL_FEATURES) {
            return
        }
        if (gradleVersion < GRADLE_VERSION_WITH_OPT_IN_FEATURES && fileName == GRADLE_PROPERTIES_FILE) {
            file << """\
                org.gradle.advancedpomsupport=true
                """.stripIndent()
            return
        }
        if (gradleVersion >= GRADLE_VERSION_WITH_OPT_IN_FEATURES && gradleVersion <= GRADLE_VERSION_WITH_DEFAULT_FEATURES && fileName == GRADLE_SETTINGS_FILE) {
            file << """\
                enableFeaturePreview('IMPROVED_POM_SUPPORT')
                """.stripIndent()
        }
    }

    static void enableExperimentalFeatures(File settingsFile, String gradleVersion) {
        if (gradleVersion >= GRADLE_VERSION_WITH_EXPERIMENTAL_FEATURES && gradleVersion < GRADLE_VERSION_WITH_OPT_IN_FEATURES) {
            settingsFile << """\
            gradle.experimentalFeatures.enable()
            """.stripIndent()
        }
    }
}