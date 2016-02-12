package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.logging.Logger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Specification

import javax.tools.*

class JavaFixture {
    List<SimpleJavaFileObject> sources = []
    def compiler = ToolProvider.getSystemJavaCompiler()

    byte[] compile(String sourceStr) {
        def className = fullyQualifiedName(sourceStr)

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

    static String fullyQualifiedName(String sourceStr) {
        def pkgMatcher = sourceStr =~ /\s*package\s+([\w\.]+)/
        def pkg = pkgMatcher.find() ? pkgMatcher[0][1] + '.' : ''

        def classMatcher = sourceStr =~ /\s*(class|interface)\s+(\w+)/
        return classMatcher.find() ? pkg + classMatcher[0][2] : null
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

class JavaFixtureSpec extends Specification {
    def 'find fully qualified name in Java source'() {
        expect:
        JavaFixture.fullyQualifiedName('''
            package myorg.a;
            public class A {
            }
        ''') == 'myorg.a.A'

        JavaFixture.fullyQualifiedName('''
            package myorg.a;
            public interface A {
            }
        ''') == 'myorg.a.A'
    }

    def 'compile java'() {
        when:
        def java = new JavaFixture()

        then:
        java.compile('''
            package myorg;
            public class A {
            }
        ''')

        java.compile('''
            package myorg;
            public class B {
                A a = new A();
            }
        ''')
    }
}