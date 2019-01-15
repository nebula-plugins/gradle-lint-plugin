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

import org.gradle.api.artifacts.ResolvedArtifact
import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

final class DependencyClassVisitor extends ClassVisitor {
    private Map<String, Collection<ResolvedArtifact>> classOwners
    private String className
    private ClassLoader loader
    private Logger logger = LoggerFactory.getLogger(DependencyClassVisitor)

    Set<ResolvedArtifact> directReferences = new HashSet()

    /**
     * References that are necessary at compile time (e.g. type hierarchy of implemented interfaces), but are satisfactory
     * as transitive dependencies. Example: guice's <code>GuiceServletContextListener</code> refers to javax.servlet's
     * <code>ServletContextListener</code>, but adding guice as a dependency does NOT result in a transitive dependency on
     * javax.servlet. In this case, we want to preserve a first order dependency on javax.servlet when
     * <code>GuiceServletContextListener</code> is extended somewhere in the code.
     */
    Set<ResolvedArtifact> indirectReferences = new HashSet()

    DependencyClassVisitor(Map<String, Collection<ResolvedArtifact>> classOwners, ClassLoader loader) {
        super(Opcodes.ASM7)
        this.classOwners = classOwners
        this.loader = loader
    }

    void readSignature(String signature) {
        if(signature)
            new SignatureReader(signature).accept(new DependencySignatureVisitor())
    }

    void readObjectName(String type, boolean indirect = false) {
        if(!type) return
        def owners = classOwners[Type.getObjectType(type).internalName] ?: Collections.emptySet()
        if(logger.isDebugEnabled()) {
            for (owner in owners) {
                logger.debug("$className refers to $type which was found in $owner")
            }
        }

        if(indirect) indirectReferences.addAll(owners)
        else directReferences.addAll(owners)
    }

    void readType(String desc) {
        if(!desc) return
        def t = Type.getType(desc)
        switch(t.sort) {
            case Type.ARRAY:
                readType(t.elementType.descriptor)
                break
            case Type.OBJECT:
                readObjectName(t.internalName)
                break
            default:
                readObjectName(desc)
        }
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name
        readObjectName(superName)
        interfaces.each { readObjectName(it) }

        if(superName) {
            try {
                ClassHierarchyUtils.typeHierarchy(Class.forName(superName.replace('/', '.'), false, loader)).each {
                    readObjectName(it.replace('.', '/'), true)
                }
            } catch(ClassNotFoundException ignored) {
                // do nothing
            } catch(NoClassDefFoundError ignored) {
                // do nothing
            }
        }
        interfaces.each { intf ->
            try {
                ClassHierarchyUtils.typeHierarchy(Class.forName(intf.replace('/', '.'), false, loader)).each {
                    readObjectName(it.replace('.', '/'), true)
                }
            } catch(ClassNotFoundException ignored) {
                // do nothing
            }
        }

        readSignature(signature)
    }

    @Override
    AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        readType(desc)
        return new DependencyAnnotationVisitor()
    }

    @Override
    AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        readType(desc)
        return new DependencyAnnotationVisitor()
    }

    @Override
    FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        readType(desc)
        readSignature(signature)
        return new DependencyFieldVisitor()
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        Type.getArgumentTypes(desc).each { readType(it.descriptor) }
        readType(Type.getReturnType(desc).descriptor)
        readSignature(signature)
        exceptions.each { readObjectName(it) }
        return new DependencyMethodVisitor()
    }

    class DependencySignatureVisitor extends SignatureVisitor {
        DependencySignatureVisitor() {
            super(Opcodes.ASM7)
        }

        @Override void visitClassType(String name) { readObjectName(name) }

        @Override SignatureVisitor visitInterfaceBound() { this }
        @Override SignatureVisitor visitClassBound() { this }
        @Override SignatureVisitor visitReturnType() { this }
        @Override SignatureVisitor visitParameterType() { this }
        @Override SignatureVisitor visitExceptionType() { this }
        @Override SignatureVisitor visitArrayType() { this }
    }

    class DependencyFieldVisitor extends FieldVisitor {
        DependencyFieldVisitor() {
            super(Opcodes.ASM7)
        }

        @Override
        AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }
    }

    class DependencyMethodVisitor extends MethodVisitor {
        DependencyMethodVisitor() {
            super(Opcodes.ASM7)
        }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            readObjectName(owner)
            readType(Type.getReturnType(desc).descriptor)
            Type.getArgumentTypes(desc).collect { readType(it.descriptor) }
        }

        @Override
        void visitFieldInsn(int opcode, String owner, String name, String desc) {
            readType(desc)
        }

        @Override
        void visitTypeInsn(int opcode, String type) {
            readObjectName(type)
        }

        @Override
        void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            readType(desc)
        }

        @Override
        AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            readType(desc)
            return super.visitAnnotation(desc, visible)
        }

        @Override
        void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            readObjectName(type)
        }

        @Override
        AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            readType(desc)
            readSignature(signature)
        }

        @Override
        AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        void visitMultiANewArrayInsn(String desc, int dims) {
            readType(desc)
        }

        @Override
        void visitLdcInsn(Object cst) {
            if(cst instanceof Type) {
                readObjectName(cst.internalName)
            }
        }
    }

    class DependencyAnnotationVisitor extends AnnotationVisitor {
        DependencyAnnotationVisitor() {
            super(Opcodes.ASM7)
        }

        @Override
        void visit(String name, Object value) {
            if(value instanceof Type)
                readObjectName(value.internalName)
        }

        @Override
        void visitEnum(String name, String desc, String value) {
            readType(desc)
        }
    }
}
