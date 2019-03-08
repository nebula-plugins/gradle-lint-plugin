package com.netflix.nebula.lint.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath

class AbstractLintTask extends DefaultTask {
    private FileCollection lintClassPath

    /**
     * Classpath containing lint dependencies for the task
     */
    @Classpath
    FileCollection getLintClassPath() {
        return lintClassPath
    }

    void setLintClassPath(FileCollection lintClassPath) {
        this.lintClassPath = lintClassPath
    }
}
