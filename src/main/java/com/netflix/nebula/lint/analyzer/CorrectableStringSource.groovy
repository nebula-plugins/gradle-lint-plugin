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

    /**
     * Only suitable for replacements that do not span more than one line
     * @param node
     * @param replacement
     */
    void inlineReplace(ASTNode node, String replacement) {
        // note that node line and column numbers are both 1 based
        def line = lines.get(node.lineNumber-1)
        lines.set(node.lineNumber-1, line.substring(0, node.getColumnNumber()-1) +
            replacement +
            line.substring(node.getLastColumnNumber()-1, line.length()))
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
