package com.netflix.nebula.lint.rule

import groovy.transform.Canonical

@Canonical
class GradleDependency {
    String group
    String name
    String version
    String classifier
    String ext
    String conf // no way to express a conf in string notation
    Syntax syntax

    enum Syntax {
        MapNotation,
        StringNotation
    }
}
