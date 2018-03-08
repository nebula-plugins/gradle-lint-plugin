package com.netflix.nebula.lint.plugin

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class SourceCollectorTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder

    def 'all build files are collected'() {
        given:
        def rootFile = temporaryFolder.newFile('build.gradle')
        def level1Sibling1 = temporaryFolder.newFile('level1Sibling1.gradle')
        def level1Sibling2 = temporaryFolder.newFile('level1Sibling2.gradle')
        def level2 = temporaryFolder.newFile('level2.gradle')
        rootFile.text = """
            apply from: 'level1Sibling1.gradle'
            apply from: 'level1Sibling2.gradle'
            apply from: 'http://DUMMY_WHICH_IS_IGNORED'
        """
        level1Sibling1.text = """
            apply from: 'level2.gradle'
        """

        when:
        def files = SourceCollector.getAllFiles(rootFile, temporaryFolder.root)

        then:
        files.containsAll([rootFile, level1Sibling1, level1Sibling2, level2])
    }
}
