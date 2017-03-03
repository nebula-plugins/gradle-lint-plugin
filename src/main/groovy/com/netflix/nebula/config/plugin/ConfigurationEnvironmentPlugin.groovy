package com.netflix.nebula.config.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class ConfigurationEnvironmentPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.create('configurationEnvironment', ConfigurationEnvironmentPrintTask)
    }
}
