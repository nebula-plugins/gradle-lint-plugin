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
