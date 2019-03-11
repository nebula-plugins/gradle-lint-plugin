package com.netflix.nebula.lint.rule.dependency

class MethodReference {
    String className
    String methodName
    String owner
    String methodDesc
    int line
    boolean isInterface

    MethodReference(String className, String methodName, String owner, String methodDesc, int line, boolean isInterface) {
        this.className = className
        this.methodName = methodName
        this.owner = owner
        this.methodDesc = methodDesc
        this.line = line
        this.isInterface = isInterface
    }

    @Override
    String toString() {
        return "className: $className | methodName: $methodName | owner: $owner | methodDesc: $methodDesc | line: $line | isInterface: $isInterface"
    }
}
