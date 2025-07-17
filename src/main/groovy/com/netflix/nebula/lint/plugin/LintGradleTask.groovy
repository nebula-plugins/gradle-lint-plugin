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

import com.google.common.annotations.VisibleForTesting
import com.netflix.nebula.lint.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.deprecation.DeprecationLogger

import java.util.function.Supplier

import static com.netflix.nebula.lint.StyledTextService.Styling.*

abstract class LintGradleTask extends DefaultTask {
    @Input
    @Optional
    List<GradleLintViolationAction> listeners = []

    @Input
    @Optional
    abstract Property<Boolean> getFailOnWarning()

    @Input
    @Optional
    abstract Property<Boolean> getOnlyCriticalRules()

    @Input
    abstract Property<File> getProjectRootDir()

    @Input
    abstract Property<ProjectTree> getProjectTree()

    @Input
    abstract Property<ProjectInfo> getProjectInfo()

    @Internal
    GradleLintInfoBrokerAction infoBrokerAction

    @Internal
    GradleLintPatchAction patchAction


    LintGradleTask() {
        failOnWarning.convention(false)
        onlyCriticalRules.convention(false)
        projectTree.set(project.provider {ProjectTree.from(this) })
        projectInfo.convention(projectTree.map(ProjectTree::getBaseProject))
        projectRootDir.set(project.rootDir)
        infoBrokerAction = new GradleLintInfoBrokerAction(this)
        patchAction = new GradleLintPatchAction(getProjectInfo().get())
        group = 'lint'
        try {
            def method = Task.getMethod("notCompatibleWithConfigurationCache")
            method.invoke(this)
        } catch (NoSuchMethodException ignore) {
        }
    }

    @TaskAction
    void lint() {
        DeprecationLogger.whileDisabled {
            def violations = new LintService().lint(projectTree.get(), onlyCriticalRules.get()).violations
                    .unique { v1, v2 -> v1.is(v2) ? 0 : 1 }

            (getListeners() + patchAction + infoBrokerAction + consoleOutputAction).each {
                it.lintFinished(violations)
            }
        }

    }

    @Internal
    final def consoleOutputAction = new GradleLintViolationAction() {
        @Override
        void lintFinished(Collection<GradleViolation> violations) {
            int errors = violations.count { it.rule.priority == 1 }
            int warnings = violations.count { it.rule.priority != 1 }

            def textOutput = new StyledTextService(getServices())

            if (!violations.empty) {
                textOutput.withStyle(Bold).text('\nThis project contains lint violations. ')
                textOutput.println('A complete listing of the violations follows. ')

                if (errors) {
                    textOutput.text('Because some were serious, the overall build status has been changed to ')
                            .withStyle(Red).println("FAILED\n")
                } else {
                    textOutput.println('Because none were serious, the build\'s overall status was unaffected.\n')
                }
            }

            violations.groupBy { it.file }.each { buildFile, violationsByFile ->

                violationsByFile.each { v ->
                    String buildFilePath = projectRootDir.get().toURI().relativize(v.file.toURI()).toString()
                    if (v.rule.priority == 1) {
                        textOutput.withStyle(Red).text('error'.padRight(10))
                    } else {
                        textOutput.withStyle(Red).text('warning'.padRight(10))
                    }

                    textOutput.text(v.rule.name.padRight(35))

                    textOutput.withStyle(Yellow).text(v.message)
                    if (v.fixes.empty) {
                        textOutput.withStyle(Yellow).text(' (no auto-fix available)')
                    }
                    if (v.documentationUri != GradleViolation.DEFAULT_DOCUMENTATION_URI) {
                        textOutput.text(". See $v.documentationUri for more details")
                    }
                    textOutput.println()

                    if (v.lineNumber) {
                        textOutput.withStyle(Bold).println(buildFilePath + ':' + v.lineNumber)
                    }
                    if (v.sourceLine) {
                        textOutput.println("$v.sourceLine")
                    }

                    textOutput.println() // extra space between violations
                }
            }

            if (!violations.empty) {
                textOutput.withStyle(Red).println("\u2716 ${errors + warnings} problem${errors + warnings == 1 ? '' : 's'} ($errors error${errors == 1 ? '' : 's'}, $warnings warning${warnings == 1 ? '' : 's'})\n".toString())
                textOutput.text("To apply fixes automatically, run ").withStyle(Bold).text("fixGradleLint")
                textOutput.println(", review, and commit the changes.\n")

                if (errors > 0) {
                    throw new GradleException("This build contains $errors critical lint violation${errors == 1 ? '' : 's'}")
                }

                if (failOnWarning.get()) {
                    throw new GradleException("This build contains $warnings lint violation${warnings == 1 ? '' : 's'}")
                }
            }
        }
    }
}
/**
 * A CC-compatible projection of project data.
 */
class ProjectInfo implements Serializable{
    String name
    String path
    File rootDir
    File buildFile
    File projectDir
    File buildDirectory
    GradleLintExtension extension
    Map<String, Object> properties
    Supplier<Project> projectSupplier

    static ProjectInfo from(Task task, Project subproject) {
        String subprojectPath = subproject.path
        return build(subproject, { task.project.project(subprojectPath) })
    }

    static ProjectInfo from(Task task) {
        return build(task.project, task::getProject)
    }

    @VisibleForTesting
    private static ProjectInfo build(Project project, Supplier<Project> projectSupplier) {
        GradleLintExtension extension =
                project.extensions.findByType(GradleLintExtension) ?:
                project.rootProject.extensions.findByType(GradleLintExtension)
        Map<String, Object> properties = [:]
        if (project.hasProperty('gradleLint.rules')) {
            properties['gradleLint.rules'] = project.property('gradleLint.rules')
        }
        if (project.hasProperty('gradleLint.excludedRules')) {
            properties['gradleLint.excludedRules'] = project.property('gradleLint.excludedRules')
        }

        return new ProjectInfo(
                name:project.name,
                path:project.path,
                rootDir:project.rootDir,
                buildFile: project.buildFile,
                projectDir:project.projectDir,
                extension: extension,
                properties: properties,
                projectSupplier: projectSupplier,
                buildDirectory : project.buildDir
        )

    }


}

class ProjectTree{
    List<ProjectInfo> allProjects

    ProjectTree(List<ProjectInfo> allProjects){
        this.allProjects = allProjects
    }

    /**
     * Returns the base project this tree was built from.
     */
    ProjectInfo getBaseProject() {
        return allProjects.head()
    }

    /**
     * Build a project tree based on the given task's project.
     *
     * @return a project tree reflecting information and the structure of the given task's project
     */
    static from(Task task) {
        def baseProject = task.project
        List<ProjectInfo> projectInfos = [ProjectInfo.from(task)] + baseProject.subprojects.collect { Project p -> ProjectInfo.from(task, p) }
        return new ProjectTree(projectInfos)
    }
}
