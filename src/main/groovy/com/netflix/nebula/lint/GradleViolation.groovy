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

import com.netflix.nebula.lint.rule.BuildFiles
import groovy.transform.EqualsAndHashCode
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation

import java.util.concurrent.atomic.AtomicInteger

@EqualsAndHashCode(includes = 'id')
class GradleViolation extends Violation {
    BuildFiles files
    List<GradleLintFix> fixes = []
    int id

    static AtomicInteger nextId = new AtomicInteger(0)

    GradleViolation(BuildFiles files, Rule rule, Integer lineNumber, String sourceLine, String message) {
        this.files = files
        this.rule = rule
        this.lineNumber = lineNumber
        this.sourceLine = sourceLine
        this.message = message
        this.id = nextId.getAndIncrement()
    }

    File getFile() {
        files.original(super.lineNumber).file
    }

    Integer getLineNumber() {
        files.original(super.lineNumber).line
    }

    GradleViolation insertAfter(ASTNode node, String changes) {
        BuildFiles.Original original = files.original(node.lastLineNumber)
        fixes += new GradleLintInsertAfter(this, original.file, original.line, changes)
        this
    }

    GradleViolation insertBefore(ASTNode node, String changes) {
        BuildFiles.Original original = files.original(node.lineNumber)
        fixes += new GradleLintInsertBefore(this, original.file, original.line, changes)
        this
    }

    GradleViolation insertIntoClosure(ASTNode node, String changes) {
        return insertIntoClosureAt(node, changes, true)
    }

    GradleViolation insertIntoClosureAtTheEnd(ASTNode node, String changes) {
        return insertIntoClosureAt(node, changes, false)
    }

    private void insertIntoClosureAt(ASTNode node, String changes, boolean insertAtStart) {
        ClosureExpression closure = null
        if (node instanceof MethodCallExpression) {
            closure = node.arguments.find { it instanceof ClosureExpression } as ClosureExpression
            if (!closure && node.arguments instanceof ArgumentListExpression) {
                (node.arguments as ArgumentListExpression).expressions.each {
                    insertIntoClosure(it, changes)
                }
            }
        } else if (node instanceof ClosureExpression) {
            closure = node
        }

        // goal: when adding into a closure, it should be indented 4 spaces
        // achieve this by taking the node's (columnNumber-1)+4 OR columnNumber+3

        if (closure) {
            if (closure.lineNumber == closure.lastLineNumber) {
                // TODO what to do about single line closures?
            } else {
                def indentedChanges = changes.stripIndent()
                        .split('\n')
                        .collect { line -> ''.padRight(node.columnNumber + 3) + line }
                        .join('\n')

                def lineNumberToStartInsertion = insertAtStart ? closure.lineNumber : closure.lastLineNumber
                BuildFiles.Original original = files.original(lineNumberToStartInsertion)
                if (insertAtStart) {
                    fixes += new GradleLintInsertAfter(this, original.file, original.line, indentedChanges)
                } else {
                    fixes += new GradleLintInsertBefore(this, original.file, original.line, indentedChanges)
                }
            }
        }

        this
    }

    GradleViolation replaceWith(ASTNode node, String changes) {
        BuildFiles.Original original = files.original(node.lineNumber)
        fixes += new GradleLintReplaceWith(this, original.file, original.line..files.original(node.lastLineNumber).line, node.columnNumber,
            node.lastColumnNumber, changes)
        this
    }

    GradleViolation delete(ASTNode node) {
        BuildFiles.Original original = files.original(node.lineNumber)
        fixes += new GradleLintReplaceWith(this, original.file, original.line..files.original(node.lastLineNumber).line, node.columnNumber, node.lastColumnNumber, '')
        this
    }

    GradleViolation insertAfter(File file, Integer afterLine, String changes) {
        fixes += new GradleLintInsertAfter(this, file, afterLine, changes)
        this
    }

    GradleViolation insertBefore(File file, Integer beforeLine, String changes) {
        fixes += new GradleLintInsertBefore(this, file, beforeLine, changes)
        this
    }

    GradleViolation replaceAll(File file, String changes) {
        def lines = file.readLines()
        fixes += new GradleLintReplaceWith(this, file, 1..lines.size(), 1, lines[-1].length() + 1, changes)
        this
    }

    GradleViolation deleteLines(File file, Range<Integer> linesToDelete) {
        fixes += new GradleLintDeleteLines(this, file, linesToDelete)
        this
    }

    GradleViolation deleteFile(File file) {
        fixes += new GradleLintDeleteFile(this, file)
        this
    }

    GradleViolation createFile(File file, String changes, FileMode fileMode = FileMode.Regular) {
        fixes += new GradleLintCreateFile(this, file, changes, fileMode)
        this
    }
}