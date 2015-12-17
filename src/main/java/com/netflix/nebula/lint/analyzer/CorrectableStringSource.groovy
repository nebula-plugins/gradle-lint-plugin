package com.netflix.nebula.lint.analyzer

import org.codehaus.groovy.ast.ASTNode
import org.codenarc.analyzer.SuppressionAnalyzer
import org.codenarc.source.AbstractSourceCode

class CorrectableStringSource extends AbstractSourceCode {
    List<String> lines

    CorrectableStringSource(String source) {
        assert source != null
        this.lines = new StringReader(source).readLines()
        setSuppressionAnalyzer(new SuppressionAnalyzer(this))
    }

    void inlineReplace(ASTNode node, String replacement) {
        // note that node line and column numbers are both 1 based
        def linesToReplace = lines.subList(node.lineNumber-1, node.lastLineNumber)

        def lastColumn = node.lastColumnNumber-1
        if(linesToReplace)
            lastColumn += linesToReplace[0..-2].sum { it.length()+1 } // +1 for the extra newline character we are going to add

        def allLines = linesToReplace.join('\n')

        // delete all the lines to be replaced
        ((node.lastLineNumber-1)..(node.lineNumber-1)).each { Integer i ->
            lines.removeAt(i)
        }

        // perform replacement
        // TODO what if the replacement itself is multiline?  split the line and add them one at a time...
        lines.add(node.lineNumber-1, allLines.substring(0, node.columnNumber-1) +
            replacement + allLines.substring(lastColumn))
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
