/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        source.correctedSource
    }
}
