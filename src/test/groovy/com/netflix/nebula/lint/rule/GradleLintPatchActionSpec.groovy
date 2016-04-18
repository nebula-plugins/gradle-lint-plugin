/*
 * Copyright 2015-2016 Netflix, Inc.
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

class GradleLintPatchActionSpec extends Specification {
    @Rule TemporaryFolder temp
    Project project

    def setup() {
        project = [getRootDir: { temp.root }] as Project
    }

    def 'single line patch'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        '''.substring(0).stripIndent()

        when:
        def fix = new GradleLintReplaceWith(f, 2..2, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
            --- a/my.txt
            +++ b/my.txt
            @@ -1,3 +1,3 @@
             a
            -b
            +*
             c
             '''.substring(1).stripIndent()
    }

    def 'partial line replacement'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = 'abc\n'

        when:
        def fix = new GradleLintReplaceWith(f, 1..1, 2, 3, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
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
        def fix = new GradleLintReplaceWith(f, 1..2, 2, 3, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
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
        generator.patch([new GradleLintReplaceWith(f, 1..1, 1, 2, '')]) == expect
        generator.patch([new GradleLintReplaceWith(f, 1..1, 1, -1, '')]) == expect
        generator.patch([new GradleLintDelete(f, 1..1)]) == expect
    }

    def 'deleting such that the entire file is empty'() {
        setup:
        def expect = '''
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
        generator.patch([new GradleLintDelete(f, 1..1)]) == expect
    }

    def 'inserting a line'() {
        when:
        def f = temp.newFile('my.txt')
        f.text = 'a\n'

        def generator = new GradleLintPatchAction(project)

        then:
        generator.patch([new GradleLintInsertAfter(f, 1, 'b')]) == '''
            --- a/my.txt
            +++ b/my.txt
            @@ -1,1 +1,2 @@
             a
            +b
            '''.substring(1).stripIndent()

        generator.patch([new GradleLintInsertBefore(f, 1, 'b')]) == '''
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
        '''.substring(0).stripIndent()

        when:
        def fix1 = new GradleLintReplaceWith(f, 1..1, 1, 2, '*')
        def fix2 = new GradleLintReplaceWith(f, 9..9, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
            --- a/my.txt
            +++ b/my.txt
            @@ -1,4 +1,4 @@
            -a
            +*
             b
             c
             d
            --- a/my.txt
            +++ b/my.txt
            @@ -6,4 +6,4 @@
             f
             g
             h
            -i
            +*
            '''.substring(1).stripIndent()
    }

    def 'overlapping patches'() {
        setup:
        def f = temp.newFile('my.txt')

        f.text = '''\
        a
        b
        c
        '''.substring(0).stripIndent()

        when:
        def fix1 = new GradleLintReplaceWith(f, 1..1, 1, 2, '*')
        def fix2 = new GradleLintReplaceWith(f, 3..3, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
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


        '''.substring(0).stripIndent()

        when:
        def fix = new GradleLintReplaceWith(f, 1..1, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
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
        '''.substring(0).stripIndent()

        when:
        def fix = new GradleLintReplaceWith(f, 3..3, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
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
        def fix1 = new GradleLintInsertAfter(f, 1, 'c')
        def fix2 = new GradleLintDelete(f, 3..3)
        def patch = new GradleLintPatchAction(project).patch([fix1, fix2])

        then:
        patch == '''
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
        b'''.substring(0).stripIndent()

        when:
        def fix = new GradleLintReplaceWith(f, 1..1, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
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
        def fix = new GradleLintReplaceWith(f, 1..1, 1, 2, '*')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
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
        def fix = new GradleLintInsertAfter(f, 1, 'b\n')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == '''
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
            --- a/my.txt
            +++ b/my.txt
            @@ -0,0 +1,1 @@
            +a
            '''.substring(1).stripIndent()

        when:
        def fix = new GradleLintInsertBefore(f, 1, 'a\n')
        def patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == expect

        when:
        fix = new GradleLintInsertAfter(f, 0, 'a\n')
        patch = new GradleLintPatchAction(project).patch([fix])

        then:
        patch == expect
    }
}
