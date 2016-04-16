package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.GradleLintViolationAction
import org.gradle.api.Incubating
import org.gradle.api.InvalidUserDataException

class GradleLintExtension {
    List<String> rules = []
    String reportFormat = 'html'

    @Incubating
    List<GradleLintViolationAction> listeners = []

    void setReportFormat(String reportFormat) {
        if (reportFormat in ['xml', 'html', 'text']) {
            this.reportFormat = reportFormat
        } else {
            throw new InvalidUserDataException("'$reportFormat' is not a valid CodeNarc report format")
        }
    }

    // pass-thru markers for the linter to know which blocks of code to ignore
    void ignore(Closure c) { c() }
    void ignore(String ruleName, Closure c) { c() }
    void ignore(String r1, String r2, Closure c) { c() }
    void ignore(String r1, String r2, String r3, Closure c) { c() }
    void ignore(String r1, String r2, String r3, String r4, Closure c) { c() }
    void ignore(String r1, String r2, String r3, String r4, String r5, Closure c) { c() }
}
