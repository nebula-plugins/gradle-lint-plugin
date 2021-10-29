package com.netflix.nebula.lint.rule

import groovy.transform.CompileStatic

import java.nio.charset.Charset
import java.nio.file.Files

/**
 * This class provides transformation from multiple build files to one concatenated text which is used for applying
 * lint rules. We keep original mapping so we can get based on line number in concatenated text original line and file.
 */
@CompileStatic
class BuildFiles {

    private Map<LineRange, File> orderedFiles = new LinkedHashMap<>()

    BuildFiles(List<File> allFiles) {
        int initialRangeTotal = 1
        int endRangeTotal = 0
        allFiles.each { File buildFile ->
            int count = Files.lines(buildFile.toPath(), Charset.defaultCharset()).count().toInteger()
            endRangeTotal += count
            LineRange range = new LineRange(initialRangeTotal, endRangeTotal)
            initialRangeTotal += count
            orderedFiles.put(range, buildFile)
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
