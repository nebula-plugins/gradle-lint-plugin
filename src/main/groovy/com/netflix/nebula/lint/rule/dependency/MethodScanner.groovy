package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.artifacts.ResolvedArtifact
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.nio.file.Path

/**
 * Scans a given class and retrieves information about method references.
 * By default, ignores java/lang package.
 */
class MethodScanner {
    private AppClassVisitor classVisitor

    private ArrayList<MethodReference> methodReferences = []

    private class AppMethodVisitor extends MethodVisitor {

        int line

        private final Collection<String> ignoredPackages
        private final Collection<String> includeOnlyPackages
        private final Map<String, Collection<ResolvedArtifact>> artifactsByClass

        AppMethodVisitor(Map<String, Collection<ResolvedArtifact>> artifactsByClass, Collection<String> includeOnlyPackages, Collection<String> ignoredPackages) {
            super(Opcodes.ASM6)
            this.ignoredPackages = ignoredPackages
            this.includeOnlyPackages = includeOnlyPackages
            this.artifactsByClass = artifactsByClass
        }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (!includeOnlyPackages.empty && includeOnlyPackages.any { included -> !owner.startsWith(included) }) {
                return
            }
            if (ignoredPackages.any { ignoredPackage -> owner.startsWith(ignoredPackage) }) {
                return
            }

            Collection<ResolvedArtifact> artifacts = artifactsByClass.get(owner)

            methodReferences.add(new MethodReference(
                    name,
                    owner,
                    desc,
                    line,
                    isInterface,
                    opcode,
                    artifacts.collect { ResolvedArtifactInfo.fromResolvedArtifact(it)}
            )
            )
        }

        void visitLineNumber(int line, Label start) {
            this.line = line
        }
    }

    private class AppClassVisitor extends ClassVisitor {

        private final AppMethodVisitor methodVisitor

        public String source
        public String className
        public String methodName
        public String methodDesc

        AppClassVisitor(Map<String, Collection<ResolvedArtifact>> artifactsByClass, Collection<String> includeOnlyPackages, Collection<String> ignoredPackages) {
            super(Opcodes.ASM6)
            this.methodVisitor = new AppMethodVisitor(artifactsByClass, includeOnlyPackages, ignoredPackages)
        }

        @Override
        void visit(int version, int access, String name,
                   String signature, String superName, String[] interfaces) {
            className = name
        }

        @Override
        void visitSource(String source, String debug) {
            this.source = source
        }

        @Override
        MethodVisitor visitMethod(int access, String name,
                                  String desc, String signature,
                                  String[] exceptions) {
            methodName = name
            methodDesc = desc
            return methodVisitor
        }
    }


    ClassInformation findMethodReferences(Map<String, Collection<ResolvedArtifact>> artifactsByClass, Path toScan, Collection<String> includeOnlyPackages, Collection<String> ignoredPackages) throws Exception {
        BufferedInputStream stream = toScan.newInputStream()
        stream.mark(Integer.MAX_VALUE)
        this.classVisitor = new AppClassVisitor(artifactsByClass, includeOnlyPackages, ignoredPackages)
        ClassReader reader = new ClassReader(stream)
        reader.accept(classVisitor, 0)
        stream.reset()
        stream.close()
        return new ClassInformation(classVisitor.source, classVisitor.className, methodReferences)
    }
}
