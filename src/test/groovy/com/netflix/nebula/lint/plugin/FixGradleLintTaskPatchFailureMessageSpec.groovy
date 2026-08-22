/*
 * Copyright 2015-2025 Netflix, Inc.
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

import com.netflix.nebula.lint.GradleLintFix
import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.UnfixedViolationReason
import com.netflix.nebula.lint.rule.BuildFiles
import org.codenarc.rule.Rule
import org.junit.Rule as JunitRule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

/**
 * Unit tests for {@link FixGradleLintTask#patchApplyFailureMessage}, kept as a plain {@link Specification}
 * so they stay fast and need no TestKit build.
 *
 * The end-to-end wiring, where a failed patch apply is caught and rethrown with this message,
 * is covered in {@link FixGradleLintTaskSpec}, which has the integration test helpers.
 */
class FixGradleLintTaskPatchFailureMessageSpec extends Specification {
    @JunitRule
    TemporaryFolder temp

    private static GradleViolation violationForRule(BuildFiles files, String ruleName, boolean fixed = true) {
        def rule = [getName: { ruleName }] as Rule
        def violation = new GradleViolation(files, rule, 1, 'doesnotmatter', 'doesnotmatter')
        def fix = new GradleLintFix() {
            Integer from() { 1 }

            Integer to() { 1 }

            String changes() { '' }
        }
        if (!fixed) {
            fix.markAsUnfixed(UnfixedViolationReason.OverlappingPatch)
        }
        violation.fixes = [fix]
        violation
    }

    def 'lists the names of rules whose fixes were part of the failed patch, and includes the underlying error'() {
        setup:
        def files = new BuildFiles([temp.newFile('build.gradle')])
        def violations = [
                violationForRule(files, 'remove-jacoco-plugin'),
                violationForRule(files, 'use-test-task-lazy-apis')
        ]
        def cause = new Exception('Cannot apply: HunkHeader[73,11->73,12]')

        expect:
        FixGradleLintTask.patchApplyFailureMessage(violations, cause) ==
                "Lint tried to apply the following rules, but the git patch could not be applied: " +
                "[remove-jacoco-plugin, use-test-task-lazy-apis]. " +
                "See full error: Cannot apply: HunkHeader[73,11->73,12]"
    }

    def 'excludes rules whose fixes were all marked as not fixed'() {
        setup:
        def files = new BuildFiles([temp.newFile('build.gradle')])
        def violations = [
                violationForRule(files, 'remove-jacoco-plugin'),
                violationForRule(files, 'dependency-parentheses', false)
        ]
        def cause = new Exception('Cannot apply: HunkHeader[27,13->27,13]')

        expect:
        FixGradleLintTask.patchApplyFailureMessage(violations, cause) ==
                "Lint tried to apply the following rules, but the git patch could not be applied: " +
                "[remove-jacoco-plugin]. " +
                "See full error: Cannot apply: HunkHeader[27,13->27,13]"
    }
}
