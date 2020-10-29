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

import com.netflix.nebula.interop.GradleKt
import org.gradle.BuildAdapter
import org.gradle.api.BuildCancelledException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraphListener

class GradleLintPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        if (project.buildFile.name.toLowerCase().endsWith('.kts')) {
            throw new BuildCancelledException("Gradle Lint Plugin currently doesn't support kotlin build scripts." +
                    " Please, switch to groovy build script if you want to use linting.")
        }

        LintRuleRegistry.classLoader = getClass().classLoader

        if (GradleKt.versionCompareTo(project.gradle, '5.0') >= 0) {
            new GradleLintPluginTaskConfigurer().configure(project)
        } else {
            new LegacyGradleLintPluginTaskConfigurer().configure(project)
        }
        if (GradleKt.versionCompareTo(project.gradle, '6.6') >= 0) {
            if (project == project.rootProject) {
                def emitterPlugin = Class.forName('com.netflix.nebula.lint.GradleLintDeprecationEmitterPlugin')
                project.plugins.apply(emitterPlugin)
            }
        }
    }

    protected static abstract class LintListener extends BuildAdapter implements TaskExecutionGraphListener {}
}
