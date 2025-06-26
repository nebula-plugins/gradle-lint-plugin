package com.netflix.nebula.lint.plugin.report.internal

import com.netflix.nebula.lint.plugin.GradleLintReportTask
import com.netflix.nebula.lint.plugin.report.LintReport
import org.codenarc.report.AbstractReportWriter
import org.codenarc.report.HtmlReportWriter
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

abstract class LintHtmlReport extends LintReport {
    @Inject
    LintHtmlReport(ObjectFactory objects, GradleLintReportTask task) {
        super(objects, task)
    }

    @Override
    String getName() {
        return "html"
    }

    @Override
    AbstractReportWriter getWriter() {
        def writer = new HtmlReportWriter()
        writer.outputFile = outputLocation.get().asFile
        return writer
    }
}
