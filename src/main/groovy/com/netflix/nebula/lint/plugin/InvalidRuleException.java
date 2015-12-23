package com.netflix.nebula.lint.plugin;

import org.gradle.api.GradleException;

/**
 * Thrown when a rule is found to be invalid when it is loaded.
 */
public class InvalidRuleException extends GradleException {

    public InvalidRuleException(String message) {
        super(message);
    }

    public InvalidRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
