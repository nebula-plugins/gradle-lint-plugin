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

package com.netflix.nebula.lint.rule.dependency

import spock.lang.Specification

class ClassHierarchyUtilsSpec extends Specification {
    def java = new JavaFixture()

    def 'interface hierarchy'() {
        when:
        java.compile('''
            package a;
            public interface AInt {}
        ''')

        java.compile('''
            package b;
            public interface BInt extends a.AInt {}
        ''')

        java.compile('''
            package c;
            public class C implements b.BInt {}
        ''')

        then:
        ClassHierarchyUtils.typeHierarchy(java.classLoader.findClass('c.C')).sort() == ['a.AInt', 'b.BInt']
    }

    def 'class hierarchy'() {
        when:
        java.compile('''
            package a;
            public class A {}
        ''')

        java.compile('''
            package b;
            public class B extends a.A {}
        ''')

        java.compile('''
            package c;
            public class C extends b.B {}
        ''')

        then:
        ClassHierarchyUtils.typeHierarchy(java.classLoader.findClass('c.C')).sort() == ['a.A', 'b.B']
    }
}
