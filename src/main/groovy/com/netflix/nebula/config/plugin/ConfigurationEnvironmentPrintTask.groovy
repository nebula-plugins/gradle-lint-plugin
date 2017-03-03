package com.netflix.nebula.config.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ConfigurationEnvironmentPrintTask extends DefaultTask {
    @TaskAction
    void printConfigurationEnvironment() {
        def confExtensions = project.configurations.inject([]) { acc, conf ->
            acc += conf.extendsFrom.collect { extended -> "$conf.name->$extended.name" }
            acc
        }

        println(new DependencyHierarchyWriter().printHierarchy(*confExtensions))
    }
}
