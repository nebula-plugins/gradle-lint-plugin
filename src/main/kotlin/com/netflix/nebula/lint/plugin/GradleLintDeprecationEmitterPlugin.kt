package com.netflix.nebula.lint.plugin

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.operations.BuildOperationProgressEventEmitter

/**
 * Plugin that emits lint violations as deprecations.
 *
 * Requires Gradle 6.6 and later. We use [BuildOperationProgressEventEmitter] because it allows us to bypass the assumptions of Gradle's deprecation logging builder and avoid our violations being
 * treated as Gradle deprecations.
 */
@Deprecated("This uses internal APIs and should be avoided")
class GradleLintDeprecationEmitterPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project !== project.rootProject) {
            throw GradleException("This plugin can only be applied to the root project")
        }
        project.logger.debug("GradleLintDeprecationEmitterPlugin is a no-op plugin now")
    }
}
