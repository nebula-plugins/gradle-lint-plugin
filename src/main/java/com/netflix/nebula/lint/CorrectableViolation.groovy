package com.netflix.nebula.lint

import org.codenarc.rule.Violation

public abstract class CorrectableViolation extends Violation {
    abstract List<String> correct()
}