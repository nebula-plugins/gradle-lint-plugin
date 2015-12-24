package com.netflix.nebula.lint.plugin

import org.gradle.api.InvalidUserDataException

class GradleLintExtension {
    List<String> rules = []
    String reportFormat = 'html'

    void setReportFormat(String reportFormat) {
        if (reportFormat in ['xml', 'html', 'text']) {
            this.reportFormat = reportFormat
        } else {
            throw new InvalidUserDataException("'$reportFormat' is not a valid codenarc report format")
        }
    }
}
