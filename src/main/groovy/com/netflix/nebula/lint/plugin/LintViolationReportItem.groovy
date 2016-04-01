package com.netflix.nebula.lint.plugin

import groovy.transform.Canonical

@Canonical
class LintViolationReportItem {
    String buildFilePath = "unspecified"
    String ruleId = "unspecified"
    String severity = "unspecified"
    Integer lineNumber = -1
    String sourceLine = "unspecified"
}
