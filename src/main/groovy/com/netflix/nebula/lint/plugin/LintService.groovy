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

import java.util.function.Supplier

class LintService {
    def registry = new LintRuleRegistry()

    /**
     * An analyzer that can be used over and over again against multiple subprojects, compiling the results, and recording
     * the affected files according to which files the violation fixes touch
     */
    class ReportableAnalyzer extends AbstractSourceAnalyzer implements Serializable {
        DirectoryResults resultsForRootProject

        ReportableAnalyzer(File rootDir) {
            resultsForRootProject = new DirectoryResults(rootDir.absolutePath)
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

    private RuleSet ruleSetForProject(ProjectInfo p, boolean onlyCriticalRules) {
        if (p.buildFile == null || !p.buildFile.exists()) {
            LOGGER.warn("Build file for project '{}' (path: '{}') is null or does not exist. Returning empty ruleset.", p.name, p.path)
            return new ListRuleSet([])
        }

        List<String> rulesToConsider = p.effectiveRuleNames ?: []

        Supplier<Project> projectSupplier = { -> null } as Supplier<Project>

        List<Rule> includedRules = rulesToConsider.unique()
                .collect { String ruleName ->
                    this.registry.buildRules(ruleName, projectSupplier, p.criticalRuleNamesForThisProject.contains(ruleName))
                }
                .flatten() as List<Rule>

        if (onlyCriticalRules) {
            includedRules = includedRules.findAll { Rule rule -> rule.isCritical() }
        }
        List<String> excludedRuleNames = p.effectiveExcludedRuleNames ?: []

        if (!excludedRuleNames.isEmpty()) {
            includedRules.retainAll { Rule rule -> !excludedRuleNames.contains(rule.getName()) }
        }

        return RuleSetFactory.configureRuleSet(includedRules)
    }


    RuleSet ruleSet(ProjectTree projectTree) {
        def ruleSet = new CompositeRuleSet()
        projectTree.allProjects.each { ProjectInfo pInfo ->
            ruleSet.addRuleSet(ruleSetForProject(pInfo, false))
        }
        return ruleSet
    }

    Results lint(ProjectTree projectTree , boolean onlyCriticalRules) {
        if (projectTree.allProjects.isEmpty()) {
            return new DirectoryResults("empty_project_tree_results") // Return empty results
        }
        File rootDir = projectTree.allProjects.first().rootDir
        def analyzer = new ReportableAnalyzer(rootDir)
       // assert projectTree.getOrNull() != null
        //assert !projectTree.get().allProjects.empty
        //List<Project> projectsToLint = [project] + project.subprojects
        projectTree.allProjects.each {p ->
            def files = SourceCollector.getAllFiles(p.buildFile, p)
            def buildFiles = new BuildFiles(files)
            def ruleSet = ruleSetForProject(p, onlyCriticalRules)
            if (!ruleSet.rules.isEmpty()) {
                // establish which file we are linting for each rule
                ruleSet.rules.each { rule ->
                    if (rule instanceof GradleLintRule)
                        rule.buildFiles = buildFiles
                }

                analyzer.analyze(p, buildFiles.text, ruleSet)

                DependencyService.removeForProject(p)
            }
        }

        return analyzer.resultsForRootProject
    }
}
