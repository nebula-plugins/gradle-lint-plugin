package com.netflix.nebula.lint.rule;

import com.netflix.nebula.lint.analyzer.CorrectableStringSource;
import org.codenarc.rule.AbstractAstVisitor;

public abstract class AbstractLintRule extends AbstractAstVisitor {
    boolean isCorrectable() {
        return getSourceCode() instanceof CorrectableStringSource;
    }

    CorrectableStringSource getCorrectableSourceCode() {
        return (CorrectableStringSource) getSourceCode();
    }
}
