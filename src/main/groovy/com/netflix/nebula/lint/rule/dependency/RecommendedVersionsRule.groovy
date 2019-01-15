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

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import com.netflix.nebula.lint.rule.dependency.provider.MavenBomRecommendationProvider
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression

class RecommendedVersionsRule extends GradleLintRule implements GradleModelAware {
    private static final String GRADLE_VERSION_WITH_EXPERIMENTAL_FEATURES = '4.5'
    private static final String GRADLE_VERSION_WITH_OPT_IN_FEATURES = '4.6'
    private static final String GRADLE_VERSION_WITH_DEFAULT_FEATURES = '5.0'
    private static final String GRADLE_PROPERTIES = "gradle.properties"
    private static final String GRADLE_SETTINGS = "settings.gradle"
    String description = 'Remove versions from dependencies that are recommended'
    Map<String, Map<ModuleDescriptor, MethodCallExpression>> dependenciesPerConf = [:].withDefault { [:] }
    MavenBomRecommendationProvider recommendationProvider
    private Boolean recommenderIsEnabled = null

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        handleDependencyVisit(call, conf, dep)
    }

    @Override
    void visitSubprojectGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        handleDependencyVisit(call, conf, dep)
    }

    @Override
    void visitAllprojectsGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        handleDependencyVisit(call, conf, dep)
    }

    private void handleDependencyVisit(MethodCallExpression call, String conf, GradleDependency dep) {
        if (recommenderIsEnabled == null) {
            recommenderIsEnabled = recommenderIsEnabled()
        }

        if (!(ignored) && recommenderIsEnabled) {
            def desc = ModuleDescriptor.fromGradleDependency(dep)
            dependenciesPerConf.get(conf).put(desc, call)
        }
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        if (!recommenderIsEnabled) {
            return
        }

        recommendationProvider = new MavenBomRecommendationProvider(project)

        dependenciesPerConf.each { conf, allDependencies ->
            allDependencies.each { desc, call ->
                if (!desc.version.isEmpty()) {
                    if (recommendationIsAvailable(desc)) {
                        def violation = addBuildLintViolation("using a recommended dependency with a declared version, the version should be removed", call)
                        DependencyHelper.removeVersion(violation, call, new GradleDependency(desc.group, desc.name, desc.version))
                    }
                }
            }
        }
    }

    private boolean recommendationIsAvailable(ModuleDescriptor desc) {
        recommendationProvider.getVersion(desc.group, desc.name) != null
    }

    private boolean recommenderIsEnabled() {
        def gradleVersion = project.gradle.gradleVersion
        if (gradleVersion < GRADLE_VERSION_WITH_EXPERIMENTAL_FEATURES) {
            return false
        }

        def rootProjectDir = project.rootDir
        if (gradleVersion < GRADLE_VERSION_WITH_OPT_IN_FEATURES) {
            def gradlePropertiesFiles = findAllInPath(GRADLE_PROPERTIES)
            if (gradlePropertiesFiles.isEmpty()) {
                return false
            }

            boolean advancedPomPropertySet
            gradlePropertiesFiles.each {
                def props = new Properties()
                new File(it.toString()).withInputStream { props.load(it) }
                if (props.getProperty('org.gradle.advancedpomsupport') == "true") {
                    advancedPomPropertySet = true
                }
            }

            def experimentalFeaturesEnabled = project.gradle.properties.get("experimentalFeatures")
                    ?.getProperties()?.get("enabled") == true

            return advancedPomPropertySet && experimentalFeaturesEnabled
        }

        if (gradleVersion < GRADLE_VERSION_WITH_DEFAULT_FEATURES) {
            if (!rootProjectDir.listFiles().toString().contains(rootProjectDir.toString() + File.separator + GRADLE_SETTINGS)) {
                return false
            }
            def settingsFile = new File(rootProjectDir, GRADLE_SETTINGS) // There is only one
            def advancedPomFeatureEnabled = settingsFile.text.contains('enableFeaturePreview(\'IMPROVED_POM_SUPPORT\')')

            advancedPomFeatureEnabled
        }

        true // gradle versions past here should enable improved pom support by default
    }

    List<File> findAllInPath(String fileName) {
        def foundFiles = new ArrayList<>()
        def curr = project.projectDir
        while (curr != project.rootDir) {
            def fileInCurrentProjectDir = curr.toString() + File.separator + fileName
            if (curr.listFiles().toString().contains(fileInCurrentProjectDir)) {
                foundFiles.add(fileInCurrentProjectDir)
            }
            curr = curr.parentFile
        }
        def fileInParentDir = project.rootDir.toString() + File.separator + fileName
        if (project.rootDir.listFiles().toString().contains(fileInParentDir)) {
            foundFiles.add(fileInParentDir)
        }
        return foundFiles
    }
}
