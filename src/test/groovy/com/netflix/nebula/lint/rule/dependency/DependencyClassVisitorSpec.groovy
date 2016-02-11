package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Specification
import spock.lang.Unroll

import javax.tools.*

class DependencyClassVisitorSpec extends Specification {
    List<SimpleJavaFileObject> sources = []
    def compiler = ToolProvider.getSystemJavaCompiler()

    def setup() {
        compile('''\
            package a;
            public class A {
                public static A create() { return new A(); }
            }
        ''')

        compile('''\
            package a;
            public interface AInt {}
        ''')
    }

    @Unroll
    def 'references are found on class fields (when field is "#field")'() {
        when:
        compile("""\
            package b;
            import a.A;
            import java.util.*;

            public class B {
                $field;
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        containsReferenceTo('b.B', ['a/A': [a1].toSet()], a1)

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
        compile("""\
            package b;
            import a.A;
            import java.util.*;

            public class B {
                $method
            }
        """)

        def a1 = gav('netflix', 'a', '1')

        then:
        containsReferenceTo('b.B', ['a/A': [a1].toSet()], a1)

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
        compile("""\
            package b;
            import a.*;
            import java.util.*;

            public class B$classSignature {
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
            package a;

            public class AFactory {
                public static A build() { return new A(); }
            }
        ''')

        when:
        compile("""\
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
        traceClass('b.B')
        containsReferenceTo('b.B', ['a/A': [a1].toSet(), 'a/AInt': [a1].toSet()], a1) == found

        where:
        methodBodyReference             | found
//        'A a = AFactory.build()'        | true
//        'Object a = new A()'            | true
//        'A[] a = new A[0]'              | true
        'A a = A.create()'              | true

        // type erasure prevents the following two references from being observable from ASM
//        'List<A> a = new ArrayList()'   | false
//        'Object a = new ArrayList<A>()' | false

        // java does not preserve left-hand types, but rather the compiler adds CHECKCAST instructions
        // wherever right-hand assignments happen (with the exception of null)
//        'A a = null'                    | false
    }

    void traceClass(String className) {
        new ClassReader(inMemoryClassFileManager.classBytes(className) as byte[]).accept(
                new TraceClassVisitor(new PrintWriter(System.out)), ClassReader.SKIP_DEBUG)
    }

    boolean containsReferenceTo(String source, Map<String, Set<ModuleVersionIdentifier>> referenceMap,
                                ModuleVersionIdentifier refersTo) {
        def visitor = new DependencyClassVisitor(referenceMap, [isDebugEnabled: { true }, debug: { d -> println d }] as Logger)
        new ClassReader(inMemoryClassFileManager.classBytes(source) as byte[]).accept(visitor, ClassReader.SKIP_DEBUG)
        return visitor.references.contains(refersTo)
    }

    ModuleVersionIdentifier gav(String g, String a, String v) { [version: v, group: g, name: a] as ModuleVersionIdentifier }

    def 'compile java'() {
        expect:
        compile('''
            package myorg;
            public class A {
            }
        ''')

        compile('''
            package myorg;
            public class B {
                A a = new A();
            }
        ''')
    }

    byte[] compile(String sourceStr) {
        def className = fqn(sourceStr)

        if(className) {
            sources.add(new SimpleJavaFileObject(URI.create("string:///${className.replaceAll(/\./, '/')}.java"), JavaFileObject.Kind.SOURCE) {
                @Override CharSequence getCharContent(boolean ignoreEncodingErrors) { sourceStr.trim() }
            })

            def diagnostics = new DiagnosticCollector<JavaFileObject>()
            if(!compiler.getTask(null, inMemoryClassFileManager, diagnostics, null, null, sources).call()) {
                for(d in diagnostics.diagnostics) {
                    println "line $d.lineNumber: ${d.getMessage(Locale.default)}"
                }
                throw new RuntimeException('compilation failed')
            }

            return inMemoryClassFileManager.classBytes(className)
        }

        return null
    }

    def 'find fully qualified name in Java source'() {
        expect:
        fqn('''
            package myorg.a;
            public class A {
            }
        ''') == 'myorg.a.A'

        fqn('''
            package myorg.a;
            public interface A {
            }
        ''') == 'myorg.a.A'
    }

    String fqn(String sourceStr) {
        def pkgMatcher = sourceStr =~ /\s*package\s+([\w\.]+)/
        def pkg = pkgMatcher.find() ? pkgMatcher[0][1] + '.' : ''

        def classMatcher = sourceStr =~ /\s*(class|interface)\s+(\w+)/
        return classMatcher.find() ? pkg + classMatcher[0][2] : null
    }

    def inMemoryClassFileManager = new ForwardingJavaFileManager(compiler.getStandardFileManager(null, null, null)) {
        Map<String, JavaClassObject> classesByName = [:]

        byte[] classBytes(String className) { classesByName[className]?.bos?.toByteArray() }

        @Override
        JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            def clazz = new JavaClassObject(className, kind)
            classesByName[className] = clazz
            return clazz
        }

        private class JavaClassObject extends SimpleJavaFileObject {
            private def bos = new ByteArrayOutputStream()

            JavaClassObject(String name, JavaFileObject.Kind kind) {
                super(URI.create("string:///${name}.class".toString()), kind)
            }

            @Override OutputStream openOutputStream() { bos }
        }
    }
}
