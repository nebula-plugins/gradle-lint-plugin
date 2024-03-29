package com.netflix.nebula.lint.rule

import groovy.transform.CompileStatic

/**
 * This class provides transformation from multiple build files to one concatenated text which is used for applying
 * lint rules. We keep original mapping so we can get based on line number in concatenated text original line and file.
 */
@CompileStatic
class BuildFiles {

    private Map<LineRange, File> orderedFiles = new LinkedHashMap<>()

    BuildFiles(List<File> allFiles) {
        int currentLinesTotal = 0
        allFiles.each { File buildFile ->
            int count = linesCount(buildFile)
            LineRange range = new LineRange(currentLinesTotal + 1, currentLinesTotal + count)
            currentLinesTotal += count
            orderedFiles.put(range, buildFile)
        }
    }

    private static int linesCount(File file) {
        /*
         Enhancement (JDK 16): The line terminator definition was changed in java.io.LineNumberReader
         More info: https://blogs.oracle.com/javamagazine/post/the-hidden-gems-in-java-16-and-java-17-from-streammapmulti-to-hexformat
        */
        if(System.getProperty("java.specification.version").isInteger() && System.getProperty("java.specification.version").toInteger() >= 16) {
           linesCountModernJdk(file)
        } else {
           linesCountOlderJdk(file)
        }
    }

    private static int linesCountModernJdk(File file) {
        boolean shouldAddOne = file.text.empty || file.text.endsWith("\n")
        file.withReader { reader ->
            LineNumberReader lineNumberReader = new LineNumberReader(reader)
            while (lineNumberReader.read() != -1) {}
            if(shouldAddOne) {
                lineNumberReader.getLineNumber() + 1
            } else {
                lineNumberReader.getLineNumber()
            }
        }
    }

    private static int linesCountOlderJdk(File file) {
        file.withReader { reader ->
            LineNumberReader lineNumberReader = new LineNumberReader(reader)
            while (lineNumberReader.read() != -1) {}
            lineNumberReader.getLineNumber() + 1
        }
    }

    String getText() {
        StringBuilder result = new StringBuilder()
        orderedFiles.each {
            result.append(it.value.text).append('\n')
        }
        result.toString()
    }

    Original original(Integer concatenatedFileLine) {
        if (concatenatedFileLine != null) {
            Map.Entry<LineRange, File> entry = orderedFiles.find { key, value ->
                key.within(concatenatedFileLine)
            }
            if (entry == null) {
                String ranges = orderedFiles
                        .collect { key, value -> "Lines $key.start - $key.end are $value.absolutePath"}
                        .join('\n')
                throw new IllegalArgumentException("Asked line in concatenated file was: $concatenatedFileLine" +
                        " but it wasn't found. Original project files were concatenated to following ranges:\n$ranges")
            }
            new Original(
                    file: entry.value,
                    line: concatenatedFileLine - (entry.key.start - 1)
            )
        } else {
            new Original(
                    file: orderedFiles.values().first(),
                    line: null as Integer
            )
        }
    }

    static class Original {
        File file
        Integer line
    }

    static private class LineRange {
        int start
        int end

        LineRange(int start, int end) {
            this.start = start
            this.end = end
        }

        boolean within(int line) {
            start <= line && line <= end
        }
    }
}
