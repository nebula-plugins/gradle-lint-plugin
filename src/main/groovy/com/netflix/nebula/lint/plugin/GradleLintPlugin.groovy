package com.netflix.nebula.lint.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class GradleLintPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create('lint', GradleLintExtension)
        project.tasks.create('lint', GradleLintTask)
        project.tasks.create('autoCorrectLint', GradleLintCorrectionTask)
    }
}
