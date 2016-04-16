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

    String patch(List<GradleLintFix> fixes) {
        List<List<GradleLintFix>> patchSets = []

        fixes.groupBy { it.affectedFile }.each { file, fileFixes ->
            List<GradleLintFix> curPatch = []
            GradleLintFix last = null

            for (f in fileFixes.sort { f1, f2 -> f1.from() <=> f2.from() ?: f1.to() <=> f2.to() ?: f1.changes() <=> f2.changes() }) {
                if (!last || f.from() - last.to() <= MIN_LINES_CONTEXT * 2) {
                    // if context lines would overlap or abut, put these fixes in the same patch
                    curPatch += f
                } else {
                    patchSets.add(curPatch)
                    curPatch = [f]
                }
                last = f
            }

            if (!curPatch.empty)
                patchSets.add(curPatch)
        }

        String combinedPatch = ''

        patchSets.eachWithIndex { patchFixes, i ->
            def file = patchFixes[0].affectedFile
            def newlineAtEndOfOriginal = file.text.empty ? false : file.text[-1] == '\n'

            def firstLineOfContext = 1

            def beforeLineCount = 0
            def afterLineCount = 0

            // generate just this patch
            def lines = [''] + file.readLines() // the extra empty line is so we don't have to do a bunch of zero-based conversions for line arithmetic
            def patch = []
            patchFixes.eachWithIndex { fix, j ->
                // 'before' context
                if (fix.from() > 0) {
                    int minBeforeLines = (j == 0 ? 3 : Math.min(3, Math.max(fix.from() - patchFixes[j - 1].to() - 3, 0)))

                    def firstLine = Math.max(fix.from() - minBeforeLines, 1)
                    def beforeContext = lines.subList(firstLine, fix.from())
                            .collect { line -> ' ' + line }
                            .dropWhile { String line -> StringUtils.isBlank(line) }

                    if(j == 0) {
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
                } else if (fix instanceof GradleLintInsertAfter && fix.afterLine == lines.size() - 1 && !newlineAtEndOfOriginal && !file.text.empty) {
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
                    int maxAfterLines = (j == patchFixes.size() - 1) ? 3 : Math.min(3, patchFixes[j + 1].from() - fix.to() - 1)

                    def lastLineOfContext = Math.min(fix.to() + maxAfterLines + 1, lines.size())
                    def afterContext = lines.subList(fix.to() + 1, lastLineOfContext)
                            .collect { line -> ' ' + line }
                            .reverse()
                            .dropWhile { String line -> StringUtils.isBlank(line) }
                            .reverse()

                    beforeLineCount += afterContext.size()
                    afterLineCount += afterContext.size()

                    patch += afterContext

                    if (j == patchFixes.size() - 1 && lastLineOfContext == lines.size() && file.text[-1] != '\n') {
                        patch += /\ No newline at end of file/
                    }
                } else if (j == patchFixes.size() - 1 && fix.changes() && fix.changes()[-1] != '\n' && !newlineAtEndOfOriginal) {
                    patch += /\ No newline at end of file/
                }
            }

            // combine it with all the other patches
            def path = project.rootDir.toPath().relativize(file.toPath()).toString()

            if (i > 0)
                combinedPatch += '\n'
            combinedPatch += """\
                --- a/$path
                +++ b/$path
                @@ -${file.text.empty ? 0 : firstLineOfContext},$beforeLineCount +${afterLineCount == 0 ? 0 : firstLineOfContext},$afterLineCount @@
            """.stripIndent() + patch.join('\n')
        }

        combinedPatch + '\n'
    }
}
