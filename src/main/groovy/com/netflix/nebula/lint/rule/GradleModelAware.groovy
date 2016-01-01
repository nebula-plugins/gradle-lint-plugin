package com.netflix.nebula.lint.rule

import org.gradle.api.Project

/**
 * Decorate lint rule visitors with this interface in order to use the
 * evaluated Gradle project model in the rule
 */
interface GradleModelAware {
    void setProject(Project project)
}