package com.netflix.nebula.lint.plugin.report.internal

import com.netflix.nebula.lint.plugin.GradleLintReportTask
import com.netflix.nebula.lint.plugin.report.LintReport
import org.codenarc.report.AbstractReportWriter
import org.codenarc.report.HtmlReportWriter
import org.codenarc.report.XmlReportWriter
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory

import javax.inject.Inject

abstract class LintXmlReport extends LintReport {
    @Inject
    LintXmlReport(ObjectFactory objects, GradleLintReportTask task) {
        super(objects, task)
    }

    @Override
    String getName() {
        return "xml"
    }

    @Override
    AbstractReportWriter getWriter() {
        def writer = new XmlReportWriter()
        writer.outputFile = outputLocation.get().asFile
        return writer
    }
}