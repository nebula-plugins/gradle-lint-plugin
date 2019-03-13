package com.netflix.nebula.lint.rule.dependency

class ClassInformation {
    String filePath
    String source
    String name

    Collection<MethodReference> methodReferences = []

    ClassInformation(String source, String name, Collection<MethodReference> methodReferences) {
        this.source = source
        this.name = name
        this.methodReferences = methodReferences
        this.filePath = calculateFilePath(source, name)
    }

    @Override
    String toString() {
        return "source: $source - filePath: $filePath - name: $name - methodReferences: ${methodReferences*.toString().join(' | ')}"
    }

    private String calculateFilePath(String source, String name) {
        String extension = getFileExtension(source)
        return name + extension
    }

    private String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".")
        if (lastIndexOf == -1) {
            return ""
        }
        return fileName.substring(lastIndexOf)
    }
}
