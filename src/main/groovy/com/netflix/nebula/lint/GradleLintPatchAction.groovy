/*
 * Copyright 2015-2016 Netflix, Inc.
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

import groovy.transform.Canonical
import org.apache.commons.lang.StringUtils
import org.gradle.api.Project

import static FileMode.Symlink
import static com.netflix.nebula.lint.PatchType.*
import static java.nio.file.Files.readSymbolicLink

@Canonical
class GradleLintPatchAction extends GradleLintViolationAction {
    Project project

    static final String PATCH_NAME = 'lint.patch'
    private final int MIN_LINES_CONTEXT = 3

    @Override
    void lintFinished(Collection<GradleViolation> violations) {
        project.buildDir.mkdirs()
        new File(project.buildDir, PATCH_NAME).withWriter { w ->
            w.write(patch(violations*.fixes.flatten() as List<GradleLintFix>))
        }
    }

    static determinePatchType(List<GradleLintFix> patchFixes) {
        if (patchFixes.size() == 1 && patchFixes.get(0) instanceof GradleLintDeleteFile)
            return Delete
        else if (patchFixes.size() == 1 && patchFixes.get(0) instanceof GradleLintCreateFile) {
            return Create
        } else {
            return Update
        }
    }

    static readFileOrSymlink(File file, FileMode mode) {
        return mode == Symlink ? [readSymbolicLink(file.toPath()).toString()] : file.readLines()
    }

    static diffHints(String relativePath, PatchType patchType, FileMode fileMode) {
        def headers = ["diff --git a/$relativePath b/$relativePath"]
        switch (patchType) {
            case Create:
                headers += "new file mode ${fileMode.mode}"
                break
            case Delete:
                headers += "deleted file mode ${fileMode.mode}"
                break
            case Update:
                // no hint necessary
                break
        }
        return headers.collect { "|$it" }.join('\n')
    }

    String patch(List<GradleLintFix> fixes) {
        List<List<GradleLintFix>> patchSets = []

        fixes.groupBy { it.affectedFile }.each { file, fileFixes ->  // internal ordering of fixes per file is maintained (file order does not)
            List<GradleLintFix> curPatch = []

            def (individualFixes, maybeCombinedFixes) = fileFixes.split { it instanceof RequiresOwnPatchset }
            individualFixes.each { patchSets.add([it] as List<GradleLintFix>) }

            GradleLintFix last = null
            for (f in maybeCombinedFixes.sort { f1, f2 -> f1.from() <=> f2.from() ?: f1.to() <=> f2.to() ?: f1.changes() <=> f2.changes() }) {
                if (!last || f.from() - last.to() <= MIN_LINES_CONTEXT * 2) {
                    // if context lines would overlap or abut, put these fixes in the same patch
                    curPatch += f
                } else {
                    patchSets.add(curPatch)
                    curPatch = [f] as List<GradleLintFix>
                }
                last = f
            }

            if (!curPatch.empty)
                patchSets.add(curPatch)
        }

        for(patchSet in patchSets) {
            patchSet.eachWithIndex{ fix, i ->
                if(i < patchSet.size() - 1) {
                    def next = patchSet[i+1]
                    def multipleInsertionsAtSameLine = fix.from() > fix.to() && next.from() > next.to()

                    if ((fix.from() <= next.from() && fix.to() >= next.to() ||
                            next.from() <= fix.from() && next.to() >= fix.to()) &&
                            !multipleInsertionsAtSameLine) {
                        next.markAsUnfixed(UnfixedViolationReason.OverlappingPatch)
                    }
                }
            }
            patchSet.retainAll { it.reasonForNotFixing == null }
        }

        String combinedPatch = ''

        def lastPathDeleted = null
        patchSets.eachWithIndex { patchFixes, i ->
            def patchType = determinePatchType(patchFixes)

            def file = patchFixes[0].affectedFile
            def fileMode = patchType == Create ? (patchFixes[0] as GradleLintCreateFile).fileMode : FileMode.fromFile(file)
            def emptyFile = file.exists() ? (lastPathDeleted == file.absolutePath || patchType == Create ||
                    readFileOrSymlink(file, fileMode).size() == 0) : true
            def newlineAtEndOfOriginal = emptyFile ? false : fileMode != Symlink && file.text[-1] == '\n'

            def firstLineOfContext = 1

            def beforeLineCount = 0
            def afterLineCount = 0

            // generate just this patch
            def lines = [''] // the extra empty line is so we don't have to do a bunch of zero-based conversions for line arithmetic
            if (!emptyFile) lines += readFileOrSymlink(file, fileMode)

            def patch = []
            patchFixes.eachWithIndex { fix, j ->
                def lastFix = j == patchFixes.size() - 1

                // 'before' context
                if (fix.from() > 0) {
                    int minBeforeLines = (j == 0 ? 3 : Math.min(3, Math.max(fix.from() - patchFixes[j - 1].to() - 3, 0)))

                    def firstLine = Math.max(fix.from() - minBeforeLines, 1)
                    def beforeContext = lines.subList(firstLine, fix.from())
                            .collect { line -> ' ' + line }
                            .dropWhile { String line -> j == 0 && StringUtils.isBlank(line) }

                    if (j == 0) {
                        firstLineOfContext = fix.from() - beforeContext.size()
                    }

                    beforeLineCount += beforeContext.size()
                    afterLineCount += beforeContext.size()
                    patch += beforeContext
                }

                firstLineOfContext = Math.min(firstLineOfContext, fix.from())

                // - lines (lines being replaced, deleted)
                if (fix instanceof GradleLintMultilineFix) {
                    def changed = lines.subList(fix.from(), fix.to() + 1).collect { line -> '-' + line }
                    patch += changed
                    beforeLineCount += changed.size()

                    if (j == 0 && fix.to() + 1 == lines.size() && !newlineAtEndOfOriginal && changed[-1] != '\n') {
                        patch += /\ No newline at end of file/
                    }
                } else if (fix instanceof GradleLintInsertAfter && fix.afterLine == lines.size() - 1 && !newlineAtEndOfOriginal && !emptyFile) {
                    patch = patch.dropRight(1)
                    patch.addAll(['-' + lines[-1], /\ No newline at end of file/, '+' + lines[-1]])
                }

                // + lines (to be included in new file)
                if (fix instanceof GradleLintReplaceWith) {
                    def replace = (GradleLintReplaceWith) fix
                    def changeLines = replace.changes.split('\n').toList()
                    def changes = changeLines.withIndex().collect { line, k ->
                        if (k == 0) {
                            def affected = lines[fix.from()]
                            line = affected.substring(0, replace.fromColumn < 0 ? affected.length() + replace.fromColumn + 1 : replace.fromColumn - 1) + line
                        }
                        if (k == changeLines.size() - 1) {
                            def affected = lines[fix.to()]
                            line += affected.substring(replace.toColumn < 0 ? affected.length() + replace.toColumn + 1 : replace.toColumn - 1)
                        }
                        !line.empty ? '+' + line : null
                    }
                    .findAll { it }

                    patch += changes
                    afterLineCount += changes.size()
                } else if (fix instanceof GradleLintInsertBefore || fix instanceof GradleLintInsertAfter) {
                    def insertions = (fix.changes as String).split('\n').collect { line -> '+' + line }
                    patch += insertions
                    afterLineCount += insertions.size()
                }

                // 'after' context
                if (fix.to() < lines.size() - 1) {
                    int maxAfterLines = lastFix ? 3 : Math.min(3, patchFixes[j + 1].from() - fix.to() - 1)

                    def lastLineOfContext = Math.min(fix.to() + maxAfterLines + 1, lines.size())
                    def afterContext = lines.subList(fix.to() + 1, lastLineOfContext)
                            .collect { line -> ' ' + line }
                            .reverse()
                            .dropWhile { String line -> lastFix && StringUtils.isBlank(line) }
                            .reverse()

                    beforeLineCount += afterContext.size()
                    afterLineCount += afterContext.size()

                    patch += afterContext

                    if (lastFix && lastLineOfContext == lines.size() && !newlineAtEndOfOriginal) {
                        patch += /\ No newline at end of file/
                    }
                } else if (lastFix && fix.changes() && fix.changes()[-1] != '\n' && !newlineAtEndOfOriginal) {
                    patch += /\ No newline at end of file/
                }
            }

            // combine it with all the other patches
            if (i > 0)
                combinedPatch += '\n'

            def relativePath = project.rootDir.toPath().relativize(file.toPath()).toString()
            def diffHeader = """\
                ${diffHints(relativePath, patchType, fileMode)}
                |--- ${patchType == Create ? '/dev/null' : 'a/' + relativePath}
                |+++ ${patchType == Delete ? '/dev/null' : 'b/' + relativePath}
                |@@ -${emptyFile ? 0 : firstLineOfContext},$beforeLineCount +${afterLineCount == 0 ? 0 : firstLineOfContext},$afterLineCount @@
                |""".stripMargin()

            combinedPatch += diffHeader + patch.join('\n')

            lastPathDeleted = patchType == Delete ? file.absolutePath : null
        }

        combinedPatch + '\n'
    }
}

enum PatchType {
    Update, Create, Delete
}