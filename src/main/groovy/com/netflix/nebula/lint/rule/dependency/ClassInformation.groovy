package com.netflix.nebula.lint.rule.dependency

class ClassInformation {
    String source
    String name

    Collection<MethodReference> methodReferences = []

    ClassInformation(String source, String name, Collection<MethodReference> methodReferences) {
        this.source = source
        this.name = name
        this.methodReferences = methodReferences
    }

    @Override
    String toString() {
        return "source: $source - name: $name - methodReferences: ${methodReferences*.toString().join(' | ')}"
    }
}
