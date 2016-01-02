package com.netflix.nebula.lint.plugin

import org.gradle.api.InvalidUserDataException

class GradleLintExtension {
    List<String> rules = []
    String reportFormat = 'html'

    void setReportFormat(String reportFormat) {
        if (reportFormat in ['xml', 'html', 'text']) {
            this.reportFormat = reportFormat
        } else {
            throw new InvalidUserDataException("'$reportFormat' is not a valid CodeNarc report format")
        }
    }

    // do nothing, these are just markers for the linter
    void ignore(Closure c) { }
    void ignore(String ruleName, Closure c) { }
    void ignore(String r1, String r2, Closure c) { }
    void ignore(String r1, String r2, String r3, Closure c) { }
    void ignore(String r1, String r2, String r3, String r4, Closure c) { }
    void ignore(String r1, String r2, String r3, String r4, String r5, Closure c) { }
}
