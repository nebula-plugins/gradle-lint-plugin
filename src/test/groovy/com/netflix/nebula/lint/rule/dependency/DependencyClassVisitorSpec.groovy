package com.netflix.nebula.lint.rule.dependency

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.tools.GroovyClass
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Specification
import spock.lang.Unroll

class DependencyClassVisitorSpec extends Specification {
    Set<GroovyClass> classpath = new TreeSet<>({ c1, c2 -> c1.name.compareTo(c2.name) } as Comparator)
    def classLoader = new ByteClassLoader(classpath)

    def setup() {
        compile('''\
            package a
            class A {}
        ''')

        compile('''\
            package a
            interface AInt {}
        ''')
    }

    @Unroll
    def 'references are found on class fields (when field is "#field")'() {
        when:
        compile("""\
            package b
            import a.A
            import java.util.*

            class B {
                $field
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        containsReferenceTo('b.B', ['a/A': [a1].toSet()], a1)

        where:
        field << [
            'A a',
            'A[] a',
            'List<A> a'
        ]
    }

    @Unroll
    def 'references are found on methods (when method is "#methodSignature")'() {
        when:
        compile("""\
            package b
            import a.A
            import java.util.*

            class B {
                $methodSignature { }
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        containsReferenceTo('b.B', ['a/A': [a1].toSet()], a1)

        where:
        methodSignature << [
            'A m()',
            'void m(A a)',
            'public <T extends A> void m(T t)',
            'A[] m()',
            'void m(A[] a)',
            'List<A> m()',
            'void m(List<A> a)'
        ]
    }

    @Unroll
    def 'references are found on class signature (when signature is "#classSignature")'() {
        when:
        compile("""\
            package b
            import a.*
            import java.util.*

            class B$classSignature {
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        containsReferenceTo('b.B', ['a/A': [a1].toSet(), 'a/AInt': [a1].toSet()], a1)

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
        compile('''\
            package a

            class AFactory {
                public static A build() { return new A(); }
            }
        ''')

        when:
        compile("""\
            package b
            import a.*
            import java.util.*

            class B {
                void foo() {
                    $methodBodyReference;
                }
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        traceClass('b.B')
        containsReferenceTo('b.B', ['a/A': [a1].toSet(), 'a/AInt': [a1].toSet()], a1) == found

        where:
        methodBodyReference             | found
        'A a = AFactory.build()'        | true
        'Object a = new A()'            | true
        'A[] a = new A[0]'              | true

        // type erasure prevents the following two references from being observable from ASM
        'List<A> a = new ArrayList()'   | false
        'Object a = new ArrayList<A>()' | false
    }

    void traceClass(String className) {
        new ClassReader(classpath.find { it.name == className }.bytes).accept(
                new TraceClassVisitor(new PrintWriter(System.out)), ClassReader.SKIP_DEBUG)
    }

    boolean containsReferenceTo(String source, Map<String, Set<ModuleVersionIdentifier>> referenceMap,
                                ModuleVersionIdentifier refersTo) {
        def visitor = new DependencyClassVisitor(referenceMap)
        new ClassReader(classpath.find { it.name == source }.bytes).accept(visitor, ClassReader.SKIP_DEBUG)
        return visitor.references.contains(refersTo)
    }

    ModuleVersionIdentifier gav(String g, String a, String v) { [version: v, group: g, name: a] as ModuleVersionIdentifier }

    Collection<GroovyClass> compile(String classSource) { compile([classSource]) }

    Collection<GroovyClass> compile(Collection<String> classSources) {
        def cu = new CompilationUnit(new GroovyClassLoader(classLoader))
        classSources.each { cu.addSource(UUID.randomUUID().toString(), it) }
        cu.compile(Phases.CLASS_GENERATION)
        classpath.addAll(cu.classes)
        cu.classes
    }

    class ByteClassLoader extends ClassLoader {
        Collection<GroovyClass> classpath = []

        ByteClassLoader(Collection<GroovyClass> classpath) {
            super(Thread.currentThread().contextClassLoader)
            this.classpath = classpath
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                def b = classpath.find { it.name == name }.bytes
                return defineClass(name, b, 0, b.length)
            }
            catch(e) {
                throw new ClassNotFoundException(name)
            }
        }
    }
}
