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

import org.gradle.api.artifacts.ResolvedDependency
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Specification

import javax.tools.*

class JavaFixture {
    List<SimpleJavaFileObject> sources = []
    def compiler = ToolProvider.getSystemJavaCompiler()
    def diagnostics = new DiagnosticCollector<JavaFileObject>()

    void compileToClassesDir(String sourceStr, File classesDir, String... options) {
        def bytecodes = compile(sourceStr, options)
        def nameParts = fullyQualifiedName(sourceStr).split(/\./)

        def dir
        if(nameParts.size() > 1)
            dir = new File(classesDir, nameParts[0..-1].join('/'))
        else
            dir = new File(classesDir, nameParts[0])

        dir.mkdirs()
        new File(dir, nameParts[-1]).text = bytecodes
    }

    byte[] compile(String sourceStr, String... options) {
        def className = fullyQualifiedName(sourceStr)

        if(className) {
            sources.add(new SimpleJavaFileObject(URI.create("string:///${className.replaceAll(/\./, '/')}.java"), JavaFileObject.Kind.SOURCE) {
                @Override CharSequence getCharContent(boolean ignoreEncodingErrors) { sourceStr.trim() }
            })

            if(!compiler.getTask(null, inMemoryClassFileManager, diagnostics, options.toList(), null, sources).call()) {
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

    boolean containsReferenceTo(String source, Map<String, Set<ResolvedDependency>> referenceMap,
                                ResolvedDependency refersTo) {
        def visitor = new DependencyClassVisitor(referenceMap, classLoader)
        new ClassReader(inMemoryClassFileManager.classBytes(source) as byte[]).accept(visitor, ClassReader.SKIP_DEBUG)
        return visitor.directReferences.contains(refersTo)
    }

    boolean containsIndirectReferenceTo(String source, Map<String, Set<ResolvedDependency>> referenceMap,
                                ResolvedDependency refersTo) {
        def visitor = new DependencyClassVisitor(referenceMap, classLoader)
        new ClassReader(inMemoryClassFileManager.classBytes(source) as byte[]).accept(visitor, ClassReader.SKIP_DEBUG)
        return visitor.indirectReferences.contains(refersTo)
    }

    def classLoader = new ClassLoader() {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            def b = inMemoryClassFileManager.classBytes(name)
            if(b) {
                return defineClass(name, b, 0, b.length)
            } else throw new ClassNotFoundException(name)
        }
    }

    def inMemoryClassFileManager = new ForwardingJavaFileManager(compiler.getStandardFileManager(diagnostics, null, null)) {
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