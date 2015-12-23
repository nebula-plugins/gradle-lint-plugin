package com.netflix.nebula.lint.plugin

import groovy.transform.Canonical
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportContainer
import org.gradle.api.tasks.Input

@Canonical
class GradleLintExtension /*implements Reporting<GradleLintReportContainer>*/ {
    @Input
    List<String> rules = []

//    @Override
//    GradleLintReportContainer getReports() {
//        return null
//    }
//
//    @Override
//    GradleLintReportContainer reports(Closure closure) {
//        return null
//    }
}

interface GradleLintReportContainer extends ReportContainer<Report> {
}
