package com.netflix.nebula.lint.rule.dependency

class MethodReference {
    String className
    String methodName
    String owner
    String methodDesc
    int line
    boolean isInterface
    OpCode opCode

    MethodReference(String className, String methodName, String owner, String methodDesc, int line, boolean isInterface, int code) {
        this.className = className
        this.methodName = methodName
        this.owner = owner
        this.methodDesc = methodDesc
        this.line = line
        this.isInterface = isInterface
        this.opCode = OpCode.findByCode(code)
    }

    @Override
    String toString() {
        return "className: $className | methodName: $methodName | owner: $owner | methodDesc: $methodDesc | line: $line | isInterface: $isInterface | opCode: ${opCode.name()}"
    }

    enum OpCode {
        INVOKEVIRTUAL(182),
        INVOKESPECIAL(183),
        INVOKESTATIC(184),
        INVOKEINTERFACE(185),
        INVOKEDYNAMIC(186)

        private int code

        OpCode(int code) {
            this.code = code
        }

        static findByCode(int code) {
            values().find { it.code == code }
        }
    }
}
