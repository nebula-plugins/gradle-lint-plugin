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

package com.netflix.nebula.lint.plugin;

import com.netflix.nebula.lint.GradleLintViolationAction;
import groovy.lang.Closure;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GradleLintExtension {
    private List<String> rules = new ArrayList<>();

    /**
     * Allows for the exclusion of individual rules when we include rule sets
     */
    private List<String> excludedRules = new ArrayList<>();

    /**
     * Rules that, when violated, cause the build to fail
     */
    private List<String> criticalRules = new ArrayList<>();

    private String reportFormat = "html";
    private boolean reportOnlyFixableViolations = false;
    private boolean alwaysRun = true;
    private boolean autoLintAfterFailure = true;

    private List<String> skipForTasks = new ArrayList<>(Arrays.asList(
            "help", "tasks", "dependencies", "dependencyInsight", "components", 
            "model", "projects", "properties", "wrapper"
    ));

    @Incubating
    private List<GradleLintViolationAction> listeners = new ArrayList<>();

    public List<String> getRules() {
        return rules;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }

    public List<String> getExcludedRules() {
        return excludedRules;
    }

    public void setExcludedRules(List<String> excludedRules) {
        this.excludedRules = excludedRules;
    }

    public List<String> getCriticalRules() {
        return criticalRules;
    }

    public void setCriticalRules(List<String> criticalRules) {
        this.criticalRules = criticalRules;
    }

    public String getReportFormat() {
        return reportFormat;
    }

    public void setReportFormat(String reportFormat) {
        if (Arrays.asList("xml", "html", "text").contains(reportFormat)) {
            this.reportFormat = reportFormat;
        } else {
            throw new InvalidUserDataException("'" + reportFormat + "' is not a valid CodeNarc report format");
        }
    }

    public boolean getReportOnlyFixableViolations() {
        return reportOnlyFixableViolations;
    }

    public void setReportOnlyFixableViolations(boolean reportOnlyFixableViolations) {
        this.reportOnlyFixableViolations = reportOnlyFixableViolations;
    }

    public boolean getAlwaysRun() {
        return alwaysRun;
    }

    public void setAlwaysRun(boolean alwaysRun) {
        this.alwaysRun = alwaysRun;
    }

    public boolean getAutoLintAfterFailure() {
        return autoLintAfterFailure;
    }

    public void setAutoLintAfterFailure(boolean autoLintAfterFailure) {
        this.autoLintAfterFailure = autoLintAfterFailure;
    }

    public List<String> getSkipForTasks() {
        return skipForTasks;
    }

    public void setSkipForTasks(List<String> skipForTasks) {
        this.skipForTasks = skipForTasks;
    }

    @Incubating
    public List<GradleLintViolationAction> getListeners() {
        return listeners;
    }

    @Incubating
    public void setListeners(List<GradleLintViolationAction> listeners) {
        this.listeners = listeners;
    }

    // pass-thru markers for the linter to know which blocks of code to ignore
    public void ignore(Closure<?> c) {
        c.call();
    }

    public void ignore(String ruleName, Closure<?> c) {
        c.call();
    }

    public void ignore(String r1, String r2, Closure<?> c) {
        c.call();
    }

    public void ignore(String r1, String r2, String r3, Closure<?> c) {
        c.call();
    }

    public void ignore(String r1, String r2, String r3, String r4, Closure<?> c) {
        c.call();
    }

    public void ignore(String r1, String r2, String r3, String r4, String r5, Closure<?> c) {
        c.call();
    }

    public void fixme(String ignoreUntil, Closure<?> c) {
        c.call();
    }

    public void fixme(String ignoreUntil, String ruleName, Closure<?> c) {
        c.call();
    }

    public void fixme(String ignoreUntil, String r1, String r2, Closure<?> c) {
        c.call();
    }

    public void fixme(String ignoreUntil, String r1, String r2, String r3, Closure<?> c) {
        c.call();
    }

    public void fixme(String ignoreUntil, String r1, String r2, String r3, String r4, Closure<?> c) {
        c.call();
    }

    public void fixme(String ignoreUntil, String r1, String r2, String r3, String r4, String r5, Closure<?> c) {
        c.call();
    }

    public void skipForTask(String taskName) {
        skipForTasks.add(taskName);
    }
}
