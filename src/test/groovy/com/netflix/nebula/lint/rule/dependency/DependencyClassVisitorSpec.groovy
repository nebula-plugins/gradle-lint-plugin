package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.artifacts.ModuleVersionIdentifier
import spock.lang.Specification
import spock.lang.Unroll

class DependencyClassVisitorSpec extends Specification {
    JavaFixture java = new JavaFixture()

    def setup() {
        java.compile('''\
            package a;
            public class A {
                public static A create() { return new A(); }
            }
        ''')

        java.compile('''\
            package a;
            public interface AInt {}
        ''')
    }

    @Unroll
    def 'references are found on class fields (when field is "#field")'() {
        when:
        java.compile("""\
            package b;
            import a.A;
            import java.util.*;

            public class B {
                $field;
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        java.containsReferenceTo('b.B', ['a/A': [a1].toSet()], a1)

        where:
        field << [
            'A a',
            'A[] a',
            'List<A> a',
            'Object a = A.create()'
        ]
    }

    @Unroll
    def 'references are found on methods (when method is "#method")'() {
        when:
        java.compile("""\
            package b;
            import a.A;
            import java.util.*;

            public class B {
                $method
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        java.containsReferenceTo('b.B', ['a/A': [a1].toSet()], a1)

        where:
        method << [
            'A m() { return null; }',
            'void m(A a) {}',
            'public <T extends A> void m(T t) {}',
            'A[] m() { return null; }',
            'void m(A[] a) {}',
            'List<A> m() { return null; }',
            'void m(List<A> a) {}'
        ]
    }

    @Unroll
    def 'references are found on class signature (when signature is "#classSignature")'() {
        when:
        java.compile("""\
            package b;
            import a.*;
            import java.util.*;

            public class B$classSignature {
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        java.containsReferenceTo('b.B', ['a/A': [a1].toSet(), 'a/AInt': [a1].toSet()], a1)

        where:
        classSignature << [
            ' extends A',
            ' implements AInt',
            '<T extends A>'
        ]
    }

    @Unroll
    def 'references are found inside method bodies (when instance is "#methodBodyReference")'() {
        setup:
        java.compile('''\
            package a;

            public class AFactory {
                public static A build() { return new A(); }
            }
        ''')

        when:
        java.compile("""\
            package b;
            import a.*;
            import java.util.*;

            public class B {
                void foo() {
                    $methodBodyReference;
                }
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        java.containsReferenceTo('b.B', ['a/A': [a1].toSet(), 'a/AInt': [a1].toSet()], a1) == found

        where:
        methodBodyReference             | found
        'A a = AFactory.build()'        | true
        'Object a = new A()'            | true
        'A[] a = new A[0]'              | true
        'A a = A.create()'              | true

        // type erasure prevents the following two references from being observable from ASM
        'List<A> a = new ArrayList()'   | false
        'Object a = new ArrayList<A>()' | false

        // java does not preserve left-hand types, but rather the compiler adds CHECKCAST instructions
        // wherever right-hand assignments happen (with the exception of null)
        'A a = null'                    | false
    }

    ModuleVersionIdentifier gav(String g, String a, String v) { [version: v, group: g, name: a] as ModuleVersionIdentifier }
}
