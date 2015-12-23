package com.netflix.nebula.lint.plugin

import org.gradle.api.reporting.Report
import org.gradle.api.reporting.ReportContainer

class GradleLintExtension /*implements Reporting<GradleLintReportContainer>*/ {
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
