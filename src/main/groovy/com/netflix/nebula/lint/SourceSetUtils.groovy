package com.netflix.nebula.lint

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer

class SourceSetUtils {
    static boolean hasSourceSets(Project project) {
        return project.extensions.findByType(JavaPluginExtension)
    }

    static SourceSetContainer getSourceSets(Project project) {
        return project.extensions.getByType(JavaPluginExtension).sourceSets
    }
}
