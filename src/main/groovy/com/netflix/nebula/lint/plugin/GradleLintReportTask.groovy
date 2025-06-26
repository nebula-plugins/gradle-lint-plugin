/*
 * Copyright 2015-2019 Netflix, Inc.
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
package com.netflix.nebula.lint.plugin

import com.netflix.nebula.lint.GradleLintPatchAction
import com.netflix.nebula.lint.StyledTextService
import com.netflix.nebula.lint.plugin.report.LintReport
import com.netflix.nebula.lint.plugin.report.internal.LintHtmlReport
import com.netflix.nebula.lint.plugin.report.internal.LintTextReport
import com.netflix.nebula.lint.plugin.report.internal.LintXmlReport
import org.codenarc.AnalysisContext
import org.codenarc.results.Results
import org.codenarc.rule.Violation
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.reflect.Instantiator

import javax.inject.Inject

import static com.netflix.nebula.lint.StyledTextService.Styling.Bold

abstract class GradleLintReportTask extends DefaultTask implements VerificationTask {

    @Input
    abstract Property<Boolean> getReportOnlyFixableViolations()

    @Internal
    final Property<String> projectName

    @Internal
    final DirectoryProperty reportsDir

    @Internal
    final NamedDomainObjectContainer<LintReport> reports

    @Internal
    abstract Property<ProjectInfo> getProjectInfo()

    @Internal
    abstract Property<ProjectTree> getProjectTree()

    @Inject
    GradleLintReportTask(ObjectFactory objects) {
        projectInfo.set(project.provider { ProjectInfo.from(project) })
        projectTree.set(project.provider { ProjectTree.from(project) })
        projectName = objects.property(String).convention(projectInfo.get().name)
        reportsDir = objects.directoryProperty()
        reports =
                objects.domainObjectContainer(
                        LintReport, { name ->
                    switch (name) {
                        case "html":
                            return objects.newInstance(LintHtmlReport, objects, this)
                        case "xml":
                            return objects.newInstance(LintXmlReport, objects, this)
                        case "text":
                            return objects.newInstance(LintTextReport, objects, this)
                        default:
                            throw new InvalidUserDataException(name + " is invalid as the report name")
                    }
                })
        reports.create('text', {
            it.required.set(true)
        })
        reports.create('xml')
        reports.create('html')
        outputs.upToDateWhen { false }
        group = 'lint'
    }

    @TaskAction
    void generateReport() {
        //TODO: address Invocation of Task.project at execution time has been deprecated.
        DeprecationLogger.whileDisabled {
            if (reports.any { it.required.isPresent() && it.required.get()}) {
                def lintService = new LintService()
                def results = lintService.lint(projectTree.get(), false)
                filterOnlyFixableViolations(results)
                def violationCount = results.violations.size()
                def textOutput = new StyledTextService(getServices())

                textOutput.text('Generated a report containing information about ')
                textOutput.withStyle(Bold).text("$violationCount lint violation${violationCount == 1 ? '' : 's'}")
                textOutput.println(' in this project')

                reports.each {
                    if(it.required.isPresent() && it.required.get()) {
                        it.write(new AnalysisContext(ruleSet: lintService.ruleSet(projectTree.get())), results)
                    }
                }

                int errors = results.violations.count { Violation v -> v.rule.priority == 1 }
                if (errors > 0) {
                    throw new GradleException("This build contains $errors critical lint violation${errors == 1 ? '' : 's'}")
                }
            }
        }
    }


    @Inject
    Instantiator getInstantiator() {
        null // see http://gradle.1045684.n5.nabble.com/injecting-dependencies-into-task-instances-td5712637.html
    }

    /**
     * Returns the reports to be generated by this task.
     */
    NamedDomainObjectContainer<LintReport> getReports() {
        reports
    }

    /**
     * Configures the reports to be generated by this task.
     */
    NamedDomainObjectContainer<LintReport> reports(Closure closure) {
        reports.configure(closure)
    }

    /**
     * Configures the reports to be generated by this task.
     */
    NamedDomainObjectContainer<LintReport> reports(Action<? super LintReport> action) {
        return action.execute(reports)
    }

    void filterOnlyFixableViolations(Results results) {
        if (reportOnlyFixableViolations.isPresent() && reportOnlyFixableViolations.get()) {
            new GradleLintPatchAction(projectInfo.get()).lintFinished(results.violations)
            List<Violation> toRemove = results.violations.findAll {
                it.fixes.size() == 0 || it.fixes.any { it.reasonForNotFixing != null }
            }
            toRemove.each {
                results.removeViolation(it)
            }
        }
    }
}
