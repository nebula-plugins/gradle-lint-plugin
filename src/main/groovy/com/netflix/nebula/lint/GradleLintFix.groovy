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

package com.netflix.nebula.lint

import java.nio.file.Files

/**
 * Fixes that implement this marker interface will generate a single patchset per fix
 */
interface RequiresOwnPatchset {}

/**
 * Used to generate a unified diff format of auto-corrections for violations
 */
abstract class GradleLintFix {
    File affectedFile
    GradleViolation violation

    UnfixedViolationReason reasonForNotFixing = null

    /**
     * @return 1-based, inclusive
     */
    abstract Integer from()

    /**
     * @return 1-based, inclusive
     */
    abstract Integer to()

    abstract String changes()

    void markAsUnfixed(UnfixedViolationReason reason) {
        reasonForNotFixing = reason
    }
}

abstract class GradleLintMultilineFix extends GradleLintFix {
    Range<Integer> affectedLines // 1-based, inclusive

    @Override
    Integer from() { affectedLines.from }

    @Override
    Integer to() { affectedLines.to }
}

class GradleLintReplaceWith extends GradleLintMultilineFix {
    Integer fromColumn // the first affected column of the first line (1-based, inclusive)
    Integer toColumn // the last affected column of the last line (1-based, exclusive)
    String changes

    GradleLintReplaceWith(GradleViolation violation, File affectedFile, Range<Integer> affectedLines,
                          Integer fromColumn, Integer toColumn, String changes) {
        this.violation = violation
        this.affectedFile = affectedFile
        this.affectedLines = affectedLines
        this.fromColumn = fromColumn
        this.toColumn = toColumn
        this.changes = changes
    }

    @Override
    String changes() { changes }
}

class GradleLintDeleteLines extends GradleLintMultilineFix {

    GradleLintDeleteLines(GradleViolation violation, File affectedFile, Range<Integer> affectedLines) {
        this.violation = violation
        this.affectedFile = affectedFile
        this.affectedLines = affectedLines
    }

    @Override
    String changes() { null }
}

class GradleLintInsertAfter extends GradleLintFix {
    Integer afterLine // 1-based
    String changes

    GradleLintInsertAfter(GradleViolation violation, File affectedFile, Integer afterLine, String changes) {
        this.violation = violation
        this.affectedFile = affectedFile
        this.afterLine = afterLine
        this.changes = changes
    }

    @Override
    Integer from() { afterLine+1 }

    @Override
    Integer to() { afterLine }

    @Override
    String changes() { changes }
}

class GradleLintInsertBefore extends GradleLintFix {
    Integer beforeLine // 1-based
    String changes

    GradleLintInsertBefore(GradleViolation violation, File affectedFile, Integer beforeLine, String changes) {
        this.violation = violation
        this.affectedFile = affectedFile
        this.beforeLine = beforeLine
        this.changes = changes
    }

    @Override
    Integer from() { beforeLine }

    @Override
    Integer to() { beforeLine-1 }

    @Override
    String changes() { changes }
}

class GradleLintDeleteFile extends GradleLintMultilineFix implements RequiresOwnPatchset {
    GradleLintDeleteFile(GradleViolation violation, File affectedFile) {
        this.violation = violation
        this.affectedFile = affectedFile
        def numberOfLines = Files.isSymbolicLink(affectedFile.toPath()) ? 1 : affectedFile.readLines().size()
        this.affectedLines = 1..numberOfLines
    }

    @Override
    String changes() { null }
}

class GradleLintCreateFile extends GradleLintInsertBefore implements RequiresOwnPatchset {
    FileMode fileMode

    GradleLintCreateFile(GradleViolation violation, File newFile, String changes, FileMode fileMode = FileMode.Regular) {
        super(violation, newFile, 1, changes)
        this.fileMode = fileMode
    }
}