package com.netflix.nebula.lint.rule

import org.junit.ClassRule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BuildFilesTest extends Specification {

    @Shared
    @ClassRule
    TemporaryFolder temporaryFolder
    @Shared
    def file1
    @Shared
    def file2
    @Shared
    def file3
    @Shared
    def file4

    def setupSpec() {
        file1 = temporaryFolder.newFile()
        file1.text = """\nline 1\n  """
        file2 = temporaryFolder.newFile()
        file2.text = """\nline 2\n"""

        file3 = temporaryFolder.newFile()
        file3.text = "\nline 3\n"

        file4 = temporaryFolder.newFile()
    }


    def 'files are correctly concatenated'() {
        when:
        def text = new BuildFiles([file1, file2, file3, file4]).text

        then:
        text == "\nline 1\n  \n\nline 2\n\n\nline 3\n\n\n"
    }

    @Unroll
    def 'original file and line is retrieved'() {
        given:
        def buildFiles = new BuildFiles([file1, file2, file3, file4])

        when:
        def original = buildFiles.original(concatenatedLine)

        then:
        original.file == expectedFile
        original.line == originalLine

        where:
        concatenatedLine | expectedFile | originalLine
        1                | file1        | 1
        2                | file1        | 2
        3                | file1        | 3
        4                | file2        | 1
        5                | file2        | 2
        6                | file2        | 3
        7                | file3        | 1
        8                | file3        | 2
        9                | file3        | 3
        10               | file4        | 1
    }

    def 'exception with details when you are asking line out of range'() {
        given:
        def buildFiles = new BuildFiles([file1, file2, file3, file4])

        when:
        buildFiles.original(11)

        then:
        def exception = thrown(IllegalArgumentException)
        def lines = exception.message.split('\n')
        lines[0] == 'Asked line in concatenated file was: 11 but it wasn\'t found. Original project files were concatenated to following ranges:'
        lines[1].startsWith("Lines 1 - 3 are ")
        lines[2].startsWith("Lines 4 - 6 are ")
        lines[3].startsWith("Lines 7 - 9 are ")
        lines[4].startsWith("Lines 10 - 10 are ")
    }
}
