package com.netflix.nebula.lint.rule

import org.codenarc.rule.Violation

class GradleViolation extends Violation {
    String replacement
    boolean shouldDelete = false
}
