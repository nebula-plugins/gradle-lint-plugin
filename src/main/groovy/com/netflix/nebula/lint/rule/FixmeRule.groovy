package com.netflix.nebula.lint.rule

import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.source.SourceCode

class FixmeRule implements Rule {
    @Override
    List<Violation> applyTo(SourceCode sourceCode) {
        throw new RuntimeException('This should never be called')
    }

    @Override
    int getPriority() {
        return 1 // violations of fixmes are always critical rule failures
    }

    @Override
    String getName() {
        return 'expired-fixme'
    }

    @Override
    int getCompilerPhase() {
        return 0 // irrelevant
    }
}
