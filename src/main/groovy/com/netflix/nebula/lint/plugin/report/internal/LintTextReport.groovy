package com.netflix.nebula.lint.plugin.report.internal

import com.netflix.nebula.lint.plugin.GradleLintReportTask
import com.netflix.nebula.lint.plugin.report.LintReport
import org.codenarc.report.AbstractReportWriter
import org.codenarc.report.TextReportWriter
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

abstract class LintTextReport extends LintReport {
    @Inject
    LintTextReport(ObjectFactory objects, GradleLintReportTask task) {
        super(objects, task)
    }

    @Override
    String getName() {
        return "text"
    }

    @Override
    AbstractReportWriter getWriter() {
        return new TextReportWriter(outputFile: outputLocation.get().asFile)
    }
}