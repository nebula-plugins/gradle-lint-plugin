package com.netflix.nebula.lint.analyzer

import org.codenarc.analyzer.AbstractSourceAnalyzer
import org.codenarc.results.Results
import org.codenarc.results.VirtualResults
import org.codenarc.ruleset.RuleSet

class CorrectableStringSourceAnalyzer extends AbstractSourceAnalyzer {
    CorrectableStringSource source

    CorrectableStringSourceAnalyzer(String source) {
        this.source = new CorrectableStringSource(source)
    }

    @Override
    Results analyze(RuleSet ruleSet) {
        List allViolations = collectViolations(source, ruleSet)
        new VirtualResults(allViolations)
    }

    @Override
    List getSourceDirectories() {
        Collections.emptyList()
    }

    String getCorrected() {
        source.text
    }
}
