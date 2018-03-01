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

    def setupSpec() {
        file1 = temporaryFolder.newFile()
        file1.text = """
        line 1
        """
        file2 = temporaryFolder.newFile()
        file2.text = """
        line 2
        """
    }


    def 'files are correctly concatenated'() {
        when:
        def text = new BuildFiles([file1, file2]).text

        then:
        text == "\n        line 1\n        \n\n        line 2\n        \n"
    }

    @Unroll
    def 'original file and line is retrieved'() {
        given:
        def buildFiles = new BuildFiles([file1, file2])

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
    }
}
