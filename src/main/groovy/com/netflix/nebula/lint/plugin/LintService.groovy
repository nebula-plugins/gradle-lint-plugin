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

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.BuildFiles
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.ModelAwareGradleLintRule
import com.netflix.nebula.lint.rule.dependency.DependencyService
import org.codenarc.analyzer.AbstractSourceAnalyzer
import org.codenarc.results.DirectoryResults
import org.codenarc.results.FileResults
import org.codenarc.results.Results
import org.codenarc.rule.Rule
import org.codenarc.ruleset.CompositeRuleSet
import org.codenarc.ruleset.ListRuleSet
import org.codenarc.ruleset.RuleSet
import org.codenarc.source.SourceString
import org.gradle.api.Project
import org.gradle.api.UnknownDomainObjectException

import java.util.function.Supplier

class LintService {
    def registry = new LintRuleRegistry()

    /**
     * An analyzer that can be used over and over again against multiple subprojects, compiling the results, and recording
     * the affected files according to which files the violation fixes touch
     */
    class ReportableAnalyzer extends AbstractSourceAnalyzer {
        DirectoryResults resultsForRootProject

        ReportableAnalyzer(ProjectInfo projectDirInfo) {
            resultsForRootProject = new DirectoryResults(projectDirInfo.projectDir.absolutePath)
        }

        Results analyze(ProjectInfo analyzedProject, String source, RuleSet ruleSet) {
            DirectoryResults results
            if (resultsForRootProject.path != analyzedProject.projectDir.absolutePath) {
                results = new DirectoryResults(analyzedProject.projectDir.absolutePath)
                resultsForRootProject.addChild(results)
            } else {
                results = resultsForRootProject
            }

            def violations = (collectViolations(new SourceString(source), ruleSet) as List<GradleViolation>)

            violations.groupBy { it.file }.each { file, fileViolations ->
                results.addChild(new FileResults(file.absolutePath, fileViolations))
                results.numberOfFilesInThisDirectory++
            }

            resultsForRootProject
        }

        @Override
        Results analyze(RuleSet ruleSet) {
            throw new UnsupportedOperationException('use the two argument form instead')
        }

        List getSourceDirectories() {
            []
        }
    }
    Project findProjectByPath(Project rootProject, String path) {
        if (rootProject.path == path) {
            return rootProject
        }
        return rootProject.subprojects.find { it.path == path }
    }

    private Supplier<Project> createProjectSupplier(Project rootProject, String projectPath) {
        return { -> findProjectByPath(rootProject, projectPath) } as Supplier<Project>
    }

    private RuleSet ruleSetForProject(ProjectInfo projectInfo, Project rootProject,boolean onlyCriticalRules) {
        if (projectInfo.buildFile.exists()) {
            def extension = projectInfo.extension

            def rules = (projectInfo.properties['gradleLint.rules'])?.toString()?.split(',')?.toList() ?:
                    extension.rules + extension.criticalRules

            Supplier<Project> projectSupplier = createProjectSupplier(rootProject, projectInfo.path)
            def includedRules = rules.unique()
                    .collect { registry.buildRules(it,projectSupplier, extension.criticalRules.contains(it)) }
                    .flatten() as List<Rule>

            if (onlyCriticalRules) {
                includedRules = includedRules.findAll { it instanceof GradleLintRule && it.critical }
            }

            def excludedRules = (projectInfo.properties['gradleLint.excludedRules']?.toString()?.split(',')?.toList() ?: []) + extension.excludedRules
            if (!excludedRules.isEmpty())
                includedRules.retainAll { !excludedRules.contains(it.name) }

            return RuleSetFactory.configureRuleSet(includedRules)
        }
        return new ListRuleSet([])
    }
    private RuleSet ruleSetForProject(Project project, boolean onlyCriticalRules) {
        return ruleSetForProject({ project } as Supplier<Project>, onlyCriticalRules)
    }


    RuleSet ruleSet(ProjectTree projectTree) {
        def ruleSet = new CompositeRuleSet()
        projectTree.allProjects.each { p ->
            ruleSet.addRuleSet(ruleSetForProject(p,projectTree, false))}
            return ruleSet
    }


    Results lint(ProjectTree projectTree,Project rootProject ,boolean onlyCriticalRules) {
        ProjectInfo rootProjectInfo = projectTree.allProjects.find { it.path == ":" }
        def analyzer = new ReportableAnalyzer(rootProjectInfo)

        projectTree.allProjects.each { p ->
            Supplier<Project> projectSupplier = createProjectSupplier(rootProject, p.path)
            def files = SourceCollector.getAllFiles(p.buildFile, p)
            def buildFiles = new BuildFiles(files)
            def ruleSet = ruleSetForProject(p,rootProject, onlyCriticalRules)
            if (!ruleSet.rules.isEmpty()) {
                boolean containsModelAwareRule = false
                // establish which file we are linting for each rule
                ruleSet.rules.each { rule ->
                    if (rule instanceof GradleLintRule) {
                        rule.buildFiles = buildFiles
                    }
                    if (rule instanceof ModelAwareGradleLintRule) {
                        containsModelAwareRule = true
                    }
                }
                analyzer.analyze(p, buildFiles.text, ruleSet)
                if (containsModelAwareRule){
                    Project project = projectSupplier.get()
                    DependencyService.removeForProject(project)
                }
            }
        }

        return analyzer.resultsForRootProject
    }
}