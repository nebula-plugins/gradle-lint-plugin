package com.netflix.nebula.lint.utils

class IndentUtils {
    static String indentText(def node, String text) {
        return (' ' * (node.columnNumber - 1)) + text
    }
}
