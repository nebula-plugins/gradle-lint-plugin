package com.netflix.nebula.lint.analyzer

import org.codehaus.groovy.ast.ASTNode
import org.codenarc.analyzer.SuppressionAnalyzer
import org.codenarc.source.AbstractSourceCode

class CorrectableStringSource extends AbstractSourceCode {
    List<String> lines

    Map<ASTNode, String> replacements = [:]
    List<ASTNode> deletions = []
    Map<ASTNode, String> additions = [:]

    CorrectableStringSource(String source) {
        assert source != null
        this.lines = new StringReader(source).readLines()
        setSuppressionAnalyzer(new SuppressionAnalyzer(this))
    }

    String getCorrectedSource() {
        def corrections = new StringBuffer()
        for(int i = 0; i < lines.size(); i++) {
            if(i > 0)
                corrections.append('\n')

            def replacement = replacements.find { it.key.lineNumber-1 == i }
            def deletion = deletions.find { it.lineNumber-1 == i }
            def addition = additions.find { it.key.lineNumber-1 == i }

            if(replacement) {
                corrections.append(doReplacement(replacement.key, replacement.value))
                i += replacement.key.lastLineNumber-replacement.key.lineNumber
            } else if(deletion) {
                i += deletion.lastLineNumber-deletion.lineNumber
            } else {
                corrections.append(lines[i])
            }

            if(addition) {
                corrections.append(addition.value)
            }
        }
        corrections.toString()
    }

    private String doReplacement(ASTNode node, String replacement) {
        // note that node line and column numbers are both 1 based
        def linesToReplace = lines.subList(node.lineNumber-1, node.lastLineNumber)

        def lastColumn = node.lastColumnNumber-1
        if(linesToReplace.size() > 1)
            lastColumn += linesToReplace[0..-2].sum { it.length()+1 } // +1 for the extra newline character we are going to add

        def allLines = linesToReplace.join('\n')

        allLines.substring(0, node.columnNumber-1) + replacement + allLines.substring(lastColumn)
    }

    void replace(ASTNode node, String replacement) {
        this.replacements[node] = replacement
    }

    void delete(ASTNode node) {
        this.deletions += node
    }

    void add(ASTNode node, String addition) {
        this.additions[node] = addition
    }

    @Override
    String getText() {
        lines.join('\n')
    }

    @Override
    List<String> getLines() {
        lines
    }

    @Override
    String toString() {
        "CorrectableSourceString[$text]"
    }

    @Override
    String getName() {
        return null
    }

    @Override
    String getPath() {
        return null
    }
}
