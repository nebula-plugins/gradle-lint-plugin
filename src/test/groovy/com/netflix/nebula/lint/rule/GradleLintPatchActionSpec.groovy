/*
 * Copyright 2015-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule

import com.netflix.nebula.lint.*
import org.gradle.api.Project
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files

class GradleLintPatchActionSpec extends Specification {
    GradleViolation violation

    @Rule
    TemporaryFolder temp
    Project project

    def setup() {
        def buildFile = temp.newFile('build.gradle')
        project = [getRootDir: { temp.root }] as Project
        violation = new GradleViolation(
                new BuildFiles([buildFile]), // does not matter
                null, // does not matter
                1, // does not matter
                'doesnotmatter',
                'doesnotmatter')
    }

    def 'single line patch'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        '''.stripIndent()

        when:
        def fix = new GradleLintReplaceWith(violation, f, 2..2, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,3 +1,3 @@
             a
            -b
            +*
             c
             '''.substring(1).stripIndent()
    }

    def 'delete file patch'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        '''.stripIndent()

        when:
        def fix = new GradleLintDeleteFile(violation, f)
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            deleted file mode 100644
            --- a/my.txt
            +++ /dev/null
            @@ -1,1 +0,0 @@
            -a
             '''.substring(1).stripIndent()
    }

    def 'create regular file patch'() {
        setup:
        def f = new File(project.rootDir, 'my.txt')

        when:
        def fix = new GradleLintCreateFile(violation, f, 'hello')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            new file mode 100644
            --- /dev/null
            +++ b/my.txt
            @@ -0,0 +1,1 @@
            +hello
            \\ No newline at end of file
             '''.substring(1).stripIndent()
    }

    def 'replaceAll patch'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        '''.stripIndent()

        def changes = '''\
        hello
        multiline
        '''.stripIndent()

        when:
        def lines = f.readLines()
        def fix = new GradleLintReplaceWith(violation, f, 1..lines.size(), 1, lines[-1].length() + 1, changes)
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,3 +1,2 @@
            -a
            -b
            -c
            +hello
            +multiline
             '''.substring(1).stripIndent()
    }

    def 'create executable file patch'() {
        setup:
        def f = new File(project.rootDir, 'exec.sh')
        f.text = 'execute me'

        when:
        def fix = new GradleLintCreateFile(violation, f, 'hello', FileMode.Executable)
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/exec.sh b/exec.sh
            new file mode 100755
            --- /dev/null
            +++ b/exec.sh
            @@ -0,0 +1,1 @@
            +hello
            \\ No newline at end of file
             '''.substring(1).stripIndent()
    }

    def 'delete symlink and replace with executable'() {
        setup:
        def f = temp.newFile('real.txt')
        f.text = 'hello world'
        def symlink = new File(project.rootDir, 'gradle')
        Files.createSymbolicLink(symlink.toPath(), f.toPath())

        when:
        def delete = new GradleLintDeleteFile(violation, symlink)
        def create = new GradleLintCreateFile(violation, new File(project.rootDir, 'gradle/some/dir.txt'), 'new file', FileMode.Executable)
        def patch = new GradleLintPatchAction(project).patch([delete, create])

        then:
        patch == """\
            diff --git a/gradle b/gradle
            deleted file mode 120000
            --- a/gradle
            +++ /dev/null
            @@ -1,1 +0,0 @@
            -${f.absolutePath}
            \\ No newline at end of file
            diff --git a/gradle/some/dir.txt b/gradle/some/dir.txt
            new file mode 100755
            --- /dev/null
            +++ b/gradle/some/dir.txt
            @@ -0,0 +1,1 @@
            +new file
            \\ No newline at end of file
            """.stripIndent()
    }


    def 'delete symlink and create file patch'() {
        setup:
        def f = temp.newFile('real.txt')
        f.text = 'hello world'
        def symlink = new File(project.rootDir, 'gradle')
        Files.createSymbolicLink(symlink.toPath(), f.toPath())

        when:
        def delete = new GradleLintDeleteFile(violation, symlink)
        def create = new GradleLintCreateFile(violation, new File(project.rootDir, 'gradle/some/dir.txt'), 'new file')
        def patch = new GradleLintPatchAction(project).patch([delete, create])

        then:
        patch == """\
            diff --git a/gradle b/gradle
            deleted file mode 120000
            --- a/gradle
            +++ /dev/null
            @@ -1,1 +0,0 @@
            -${f.absolutePath}
            \\ No newline at end of file
            diff --git a/gradle/some/dir.txt b/gradle/some/dir.txt
            new file mode 100644
            --- /dev/null
            +++ b/gradle/some/dir.txt
            @@ -0,0 +1,1 @@
            +new file
            \\ No newline at end of file
            """.stripIndent()
    }

    def 'delete and create patches'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        '''.stripIndent()

        when:
        def delFix = new GradleLintDeleteFile(violation, f)
        def createFix = new GradleLintCreateFile(violation, f, 'hello')
        def patch = new GradleLintPatchAction(project).patch([delFix, createFix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            deleted file mode 100644
            --- a/my.txt
            +++ /dev/null
            @@ -1,3 +0,0 @@
            -a
            -b
            -c
            diff --git a/my.txt b/my.txt
            new file mode 100644
            --- /dev/null
            +++ b/my.txt
            @@ -0,0 +1,1 @@
            +hello
            \\ No newline at end of file
             '''.substring(1).stripIndent()
    }

    def 'partial line replacement'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = 'abc\n'

        when:
        def fix = new GradleLintReplaceWith(violation, f, 1..1, 2, 3, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +1,1 @@
            -abc
            +a*c
            '''.substring(1).stripIndent()
    }

    def 'multiline partial replacement'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = 'abc\ndef\n'

        when:
        def fix = new GradleLintReplaceWith(violation, f, 1..2, 2, 3, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,2 +1,1 @@
            -abc
            -def
            +a*f
            '''.substring(1).stripIndent()
    }

    def 'deleting a line'() {
        setup:
        def expect = '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,2 +1,1 @@
            -a
             b
             '''.substring(1).stripIndent()

        when:
        def f = temp.newFile('my.txt')
        f.text = 'a\nb\n'

        def generator = new GradleLintPatchAction(project)

        then:
        generator.patch([new GradleLintReplaceWith(violation, f, 1..1, 1, 2, '')]) == expect
        generator.patch([new GradleLintReplaceWith(violation, f, 1..1, 1, -1, '')]) == expect
        generator.patch([new GradleLintDeleteLines(violation, f, 1..1)]) == expect
    }

    def 'deleting such that the entire file is empty'() {
        setup:
        def expect = '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +0,0 @@
            -a
            \\ No newline at end of file
            '''.substring(1).stripIndent()

        when:
        def f = temp.newFile('my.txt')
        f.text = 'a'

        def generator = new GradleLintPatchAction(project)

        then:
        generator.patch([new GradleLintDeleteLines(violation, f, 1..1)]) == expect
    }

    def 'inserting a line'() {
        when:
        def f = temp.newFile('my.txt')
        f.text = 'a\n'

        def generator = new GradleLintPatchAction(project)

        then:
        generator.patch([new GradleLintInsertAfter(violation, f, 1, 'b')]) == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +1,2 @@
             a
            +b
            '''.substring(1).stripIndent()

        generator.patch([new GradleLintInsertBefore(violation, f, 1, 'b')]) == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +1,2 @@
            +b
             a
             '''.substring(1).stripIndent()
    }

    def 'non-overlapping patches'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        d
        e
        f
        g
        h
        i
        '''.stripIndent()

        when:
        def fix1 = new GradleLintReplaceWith(violation, f, 1..1, 1, 2, '*')
        def fix2 = new GradleLintReplaceWith(violation, f, 9..9, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,9 +1,9 @@
            -a
            +*
             b
             c
             d
             e
             f
             g
             h
            -i
            +*
            '''.substring(1).stripIndent()
    }

    def 'patches whose starts overlap'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        '''.stripIndent()

        when:
        def fix1 = new GradleLintReplaceWith(violation, f, 2..3, 1, 2, 'd')
        def fix2 = new GradleLintInsertBefore(violation, f, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,3 +1,3 @@
             a
            +*
            -b
            -c
            +d
            '''.substring(1).stripIndent()
    }

    def 'patches that overlap by one line'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        d
        e
        f
        g
        '''.stripIndent()

        when:
        def fix1 = new GradleLintReplaceWith(violation, f, 1..1, 1, 2, '*')
        def fix2 = new GradleLintReplaceWith(violation, f, 7..7, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,7 +1,7 @@
            -a
            +*
             b
             c
             d
             e
             f
            -g
            +*
            '''.substring(1).stripIndent()
    }

    def 'overlapping patch context'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        '''.stripIndent()

        when:
        def fix1 = new GradleLintReplaceWith(violation, f, 1..1, 1, 2, '*')
        def fix2 = new GradleLintReplaceWith(violation, f, 3..3, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,3 +1,3 @@
            -a
            +*
             b
            -c
            +*
            '''.substring(1).stripIndent()
    }

    def 'lines that contain only whitespace are never included as the the trailing element of after context'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a


        '''.stripIndent()

        when:
        def fix = new GradleLintReplaceWith(violation, f, 1..1, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +1,1 @@
            -a
            +*
            '''.substring(1).stripIndent()
    }

    def 'lines that contain only whitespace are never included as the the leading element of before context'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\


        a
        '''.stripIndent()

        when:
        def fix = new GradleLintReplaceWith(violation, f, 3..3, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -3,1 +3,1 @@
            -a
            +*
            '''.substring(1).stripIndent()
    }

    def 'whitespace between + and - is retained'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = 'a\n\nb\n'

        when:
        def fix1 = new GradleLintInsertAfter(violation, f, 1, 'c')
        def fix2 = new GradleLintDeleteLines(violation, f, 3..3)
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,3 +1,3 @@
             a
            +c

            -b
            '''.substring(1).stripIndent().replace('\n\n', '\n \n') // line 3 needs to have a single space, but the IDE likes to strip it
    }

    def '\'no newline at end of file statement\' when after context includes the last line of a file'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b'''.stripIndent()

        when:
        def fix = new GradleLintReplaceWith(violation, f, 1..1, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,2 +1,2 @@
            -a
            +*
             b
            \\ No newline at end of file
            '''.substring(1).stripIndent()
    }

    def '\'no newline at end of file statement\' if the changed line is the last line of a file with no newline'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = 'a'

        when:
        def fix = new GradleLintReplaceWith(violation, f, 1..1, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +1,1 @@
            -a
            \\ No newline at end of file
            +*
            \\ No newline at end of file
            '''.substring(1).stripIndent()
    }

    def '\'no newline at end of file statement\' if the original file had no trailing newline but the new one does'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = 'a'

        when:
        def fix = new GradleLintInsertAfter(violation, f, 1, 'b\n')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +1,2 @@
            -a
            \\ No newline at end of file
            +a
            +b
            '''.substring(1).stripIndent()
    }

    def 'no newline statement is not present if the original file was empty'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = ''

        def expect = '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -0,0 +1,1 @@
            +a
            '''.substring(1).stripIndent()

        when:
        def fix = new GradleLintInsertBefore(violation, f, 1, 'a\n')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == expect

        when:
        fix = new GradleLintInsertAfter(violation, f, 0, 'a\n')
        patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == expect
    }

    def 'overlapping patches (patches that occupy the same line)'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        ab
        c
        '''.stripIndent()

        when:
        def fix1 = new GradleLintReplaceWith(violation, f, 1..2, 1, 2, 'e')
        def fix2 = new GradleLintReplaceWith(violation, f, 1..1, 2, 3, 'f')
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then: 'the second fix is ignored, and would be best applied on a second pass'
        patch == '''
            diff --git a/my.txt b/my.txt
            --- a/my.txt
            +++ b/my.txt
            @@ -1,2 +1,1 @@
            -ab
            -c
            +e
            '''.substring(1).stripIndent()
    }
}
