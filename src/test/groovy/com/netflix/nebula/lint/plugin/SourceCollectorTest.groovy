package com.netflix.nebula.lint.plugin

import nebula.test.ProjectSpec

class SourceCollectorTest extends ProjectSpec {


    def 'all build files are collected'() {
        given:
        def projectDir = project.projectDir
        def rootFile = new File(projectDir, 'build.gradle')
        def level1Sibling1 = new File(projectDir, 'level1Sibling1.gradle')
        def level1Sibling2 = new File(projectDir, 'level1Sibling2.gradle')
        def level2 = new File(projectDir, 'level2.gradle')
        rootFile.text = """
            apply from: 'level1Sibling1.gradle'
            apply from: 'level1Sibling2.gradle'
            apply from: 'http://DUMMY_WHICH_IS_IGNORED'
        """
        level1Sibling1.text = """
            apply from: 'level2.gradle'
        """
        level1Sibling2.text = " "
        level2.text = " "

        when:
        def files = SourceCollector.getAllFiles(rootFile, project)

        then:
        files.containsAll([rootFile, level1Sibling1, level1Sibling2, level2])
    }

    def 'all build files are collected when absolute paths with project variables are used'() {
        given:
        def projectDir = project.projectDir
        def rootFile = new File(projectDir, 'build.gradle')
        def level1Sibling1 = new File(projectDir, 'level1Sibling1.gradle')
        def level1Sibling2 = new File(projectDir, 'level1Sibling2.gradle')
        def level1Sibling3 = new File(projectDir, 'level1Sibling3.gradle')
        def level2 = new File(projectDir, 'level2.gradle')
        rootFile.text = """
            apply from: "\${project.projectDir}/level1Sibling1.gradle"
            apply from: "\${projectDir}/level1Sibling2.gradle"
            apply from: "\${rootDir}/level1Sibling3.gradle"
        """
        level1Sibling1.text = """
            apply from: "\${project.rootDir}/level2.gradle"
        """
        level1Sibling2.text = " "
        level1Sibling3.text = " "
        level2.text = " "

        when:
        def files = SourceCollector.getAllFiles(rootFile, project)

        then:
        files.containsAll([rootFile, level1Sibling1, level1Sibling2, level1Sibling3, level2])
    }
}
