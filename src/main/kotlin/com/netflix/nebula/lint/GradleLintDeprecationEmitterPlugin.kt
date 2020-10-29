package com.netflix.nebula.lint

import com.netflix.nebula.lint.plugin.LintGradleTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.deprecation.DeprecatedFeatureUsage
import org.gradle.internal.featurelifecycle.DeprecatedUsageProgressDetails
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import java.util.function.Consumer
import javax.inject.Inject

/**
 * Plugin that emits lint violations as deprecations.
 *
 * Requires Gradle 6.6 and later. We use [BuildOperationProgressEventEmitter] because it allows us to bypass the assumptions of Gradle's deprecation logging builder and avoid our violations being
 * treated as Gradle deprecations.
 */
class GradleLintDeprecationEmitterPlugin @Inject constructor(private val emitter: BuildOperationProgressEventEmitter) : Plugin<Project> {
    override fun apply(project: Project) {
        if (project !== project.rootProject) {
            throw GradleException("This plugin can only be applied to the root project")
        }
        project.tasks.withType(LintGradleTask::class.java)
                .forEach(Consumer { task: LintGradleTask ->
                    val listeners = task.listeners
                    listeners.add(DeprecationGradleLintInfoBrokerAction(emitter))
                })
    }

    private class DeprecationGradleLintInfoBrokerAction(private val emitter: BuildOperationProgressEventEmitter) : GradleLintViolationAction() {
        override fun lintFinished(violations: Collection<GradleViolation>) {
            violations.forEach(Consumer { violation: GradleViolation ->
                val details: DeprecatedUsageProgressDetails = ViolationDeprecatedUsageProgressDetails(violation)
                emitter.emitNowIfCurrent(details)
            })
        }
    }

    /*
     * There's an implicit contract between the format of these messages and export of lint violations to Nebula metrics. Tread with care.
     */
    private class ViolationDeprecatedUsageProgressDetails(private val violation: GradleViolation) : DeprecatedUsageProgressDetails {
        override fun getSummary(): String {
            val rule = violation.rule
            // Do not change the position or format of the rule name and severity in brackets
            return String.format("Lint rule was violated: %s [%s:%s]", violation.message, rule.name, rule.priority)
        }

        override fun getRemovalDetails(): String {
            return if (violation.sourceLine != null) "Source line: " + violation.sourceLine else "Refer to the full lint report for more details"
        }

        override fun getAdvice(): String? {
            return null
        }

        override fun getContextualAdvice(): String? {
            return null
        }

        override fun getDocumentationUrl(): String? {
            return violation.documentationUri
        }

        override fun getType(): String {
            return if (violation.lineNumber != null) {
                DeprecatedFeatureUsage.Type.USER_CODE_DIRECT.name
            } else {
                DeprecatedFeatureUsage.Type.BUILD_INVOCATION.name
            }
        }

        override fun getStackTrace(): List<StackTraceElement> {
            val fileName = violation.file.absolutePath
            val lineNumber = violation.lineNumber ?: -1
            // Do not change the declaringClass or methodName
            val element = StackTraceElement("build", "lint", fileName, lineNumber)
            return listOf(element)
        }
    }
}
