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

import com.netflix.nebula.lint.GradleLintViolationAction
import org.gradle.api.Incubating
import org.gradle.api.InvalidUserDataException

class GradleLintExtension {
    List<String> rules = []

    /**
     * Allows for the exclusion of individual rules when we include rule sets
     */
    List<String> excludedRules = []

    /**
     * Rules that, when violated, cause the build to fail
     */
    List<String> criticalRules = []

    String reportFormat = 'html'
    boolean alwaysRun = true
    boolean autoLintAfterFailure = true

    List<String> skipForTasks = ['help', 'tasks', 'dependencies', 'dependencyInsight', 'components', 'model', 'projects', 'properties', 'wrapper']

    @Incubating
    List<GradleLintViolationAction> listeners = []

    void setReportFormat(String reportFormat) {
        if (reportFormat in ['xml', 'html', 'text']) {
            this.reportFormat = reportFormat
        } else {
            throw new InvalidUserDataException("'$reportFormat' is not a valid CodeNarc report format")
        }
    }

    // pass-thru markers for the linter to know which blocks of code to ignore
    void ignore(Closure c) { c() }
    void ignore(String ruleName, Closure c) { c() }
    void ignore(String r1, String r2, Closure c) { c() }
    void ignore(String r1, String r2, String r3, Closure c) { c() }
    void ignore(String r1, String r2, String r3, String r4, Closure c) { c() }
    void ignore(String r1, String r2, String r3, String r4, String r5, Closure c) { c() }

    void fixme(String ignoreUntil, Closure c) { c() }
    void fixme(String ignoreUntil, String ruleName, Closure c) { c() }
    void fixme(String ignoreUntil, String r1, String r2, Closure c) { c() }
    void fixme(String ignoreUntil, String r1, String r2, String r3, Closure c) { c() }
    void fixme(String ignoreUntil, String r1, String r2, String r3, String r4, Closure c) { c() }
    void fixme(String ignoreUntil, String r1, String r2, String r3, String r4, String r5, Closure c) { c() }

    void skipForTask(String taskName) {
        skipForTasks.add(taskName)
    }
}
