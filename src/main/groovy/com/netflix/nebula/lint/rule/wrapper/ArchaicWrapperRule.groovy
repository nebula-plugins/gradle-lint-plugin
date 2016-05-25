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

import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import groovy.json.JsonSlurper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.gradle.util.GradleVersion

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by Boaz Jan on 17/05/16.
 */
class ArchaicWrapperRule extends GradleLintRule implements GradleModelAware {
    private static
    final Pattern VERSION_PATTERN = Pattern.compile("((\\d+)\\.(\\d+)+)(-(\\p{Alpha}+)-(\\d+[a-z]?))?(-(\\d{14}([-+]\\d{4})?))?");

    private GradleVersion latestGradleVersion = null
    private GradleVersion wrapperGradleVersion = null
    private boolean hasWrapperTask = false
    private boolean hasGradleVersionProperty = false

    int majorThreshold = 0
    int minorThreshold = 2
    boolean offline = false

    @Override
    String getDescription() {
        return "don't use archaic wrapper versions"
    }

    @Override
    protected void beforeApplyTo() {
        if (!offline) {
            try {
                String gradleCurrentVersion = new URL('http://services.gradle.org/versions/current').text
                def json = new JsonSlurper().parseText(gradleCurrentVersion)
                latestGradleVersion = GradleVersion.version(json.version)
            } catch (Exception ex) {
                //TODO: add info log
            }
        }
        super.beforeApplyTo()
    }

    @Override
    void visitTask(MethodCallExpression call, String name, Map<String, String> args) {
        if (args.containsKey('type') && args.get('type') == 'Wrapper') {
            hasWrapperTask = true
            bookmark('wrapperTask', call)
        }
    }

    @Override
    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop, String value) {
        if (extension == 'wrapper' && prop == 'gradleVersion') {
            hasGradleVersionProperty = true
            wrapperGradleVersion = GradleVersion.version(value)
            bookmark('gradleVersionExpression', expression)
        }
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        if (!hasWrapperTask && !hasGradleVersionProperty) {
            return
        }
        if (hasWrapperTask && !hasGradleVersionProperty) {
            addBuildLintViolation("The wrapper task is not properly configured, the 'gradleVersion' is missing.")
            //TODO: auto fix?
            return
        }
        def versionExpression = bookmark('gradleVersionExpression')

        GradleVersion executionGradleVersion = GradleVersion.version(project.gradle.getGradleVersion())
        if (wrapperGradleVersion > executionGradleVersion) {
            addBuildLintViolation("This build was executed with a Gradle version [$executionGradleVersion] older then the one defined by the build's wrapper [$wrapperGradleVersion]")
            return
        }

        String versionTitle
        GradleVersion latestKnownGradleVersion
        if (latestGradleVersion == null) {
            // In case we we're unable to fetch the latest version from the web
            // this will make sure to gracefully continue with the executed gradle
            // in case this is the wrapper then nothing will happen.
            // If it's not the wrapper and of significantly newer version, we will still warn
            versionTitle = 'execution'
            latestKnownGradleVersion = executionGradleVersion
        } else {
            versionTitle = 'latest'
            latestKnownGradleVersion = latestGradleVersion
        }

        if (latestKnownGradleVersion > wrapperGradleVersion) {
            def latestVersionParts = splitVersionParts(latestKnownGradleVersion)
            def wrapperVersionParts = splitVersionParts(wrapperGradleVersion)
            if (latestVersionParts['major'] - wrapperVersionParts['major'] > majorThreshold) {
                addBuildLintViolation("The build's wrapper is more then $majorThreshold major versions behind the ${versionTitle} Gradle version")
                        .replaceWith(versionExpression, "gradleVersion = '${latestKnownGradleVersion.getVersion()}'")
            } else if (latestVersionParts['minor'] - wrapperVersionParts['minor'] > minorThreshold) {
                addBuildLintViolation("The build's wrapper is more then $minorThreshold minor versions behind the ${versionTitle} Gradle version")
                        .replaceWith(versionExpression, "gradleVersion = '${latestKnownGradleVersion.getVersion()}'")
            }
        }
    }

    private def Map splitVersionParts(GradleVersion version) {
        Matcher matcher = VERSION_PATTERN.matcher(version.getVersion());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("'${version.getVersion()}' is not a valid Gradle version string (examples: '1.0', '1.0-rc-1')")
        }
        return [major: Integer.parseInt(matcher.group(2)), minor: Integer.parseInt(matcher.group(3))]
    }
}