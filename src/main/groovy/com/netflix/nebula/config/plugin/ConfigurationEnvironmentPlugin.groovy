package com.netflix.nebula.config.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This plugin is used to print the configuration hierarchy,
 * useful in understanding such hierarchies for the creation of
 * new lint rules that may depend on manipulating dependencies in a
 * configuration-dependent way.
 */
class ConfigurationEnvironmentPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.create('configurationEnvironment', ConfigurationEnvironmentPrintTask)
    }
}
