package com.netflix.nebula.lint.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class GradleLintTask extends DefaultTask {
    @Input
    List<String> rules

    @TaskAction
    void lint() {

    }


}
