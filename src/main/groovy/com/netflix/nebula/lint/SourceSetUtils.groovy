package com.netflix.nebula.lint

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.util.GradleVersion

class SourceSetUtils {
    static boolean hasSourceSets(Project project) {
        if (isOlderThanGradle7_1(project)) {
            return project.convention.findPlugin(JavaPluginConvention)
        } else {
            return project.extensions.findByType(JavaPluginExtension)
        }
    }

    static SourceSetContainer getSourceSets(Project project) {
        return isOlderThanGradle7_1(project) ? project.convention.getPlugin(JavaPluginConvention).sourceSets : project.extensions.getByType(JavaPluginExtension).sourceSets
    }

    private static boolean isOlderThanGradle7_1(Project project) {
        return GradleVersion.version(project.gradle.gradleVersion).compareTo(GradleVersion.version("7.1")) < 0
    }
}
