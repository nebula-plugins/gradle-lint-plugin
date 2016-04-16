package com.netflix.nebula.lint

abstract class GradleLintViolationAction {
    void lintFinished(Collection<GradleViolation> violations) {}
    void lintFixed(Collection<GradleViolation> violations) {}
}