package com.netflix.nebula.lint.rule

import spock.lang.Specification
import spock.lang.Unroll

class GradleDependencySpec extends Specification {
    @Unroll
    def 'serialize to and deserialize from String notation `#serialized`'() {
        when:
        def dep = new GradleDependency(group, name, version, classifier, ext, null, GradleDependency.Syntax.StringNotation)

        then:
        dep.toNotation() == serialized

        when:
        def deser = GradleDependency.fromConstant(serialized)

        then:
        deser.group == group
        deser.name == name
        deser.version == version
        deser.classifier == classifier
        deser.ext == ext

        where:
        group | name | version | classifier | ext   | serialized
        'a'   | 'a'  | '1'     | 'tests'    | 'jar' | 'a:a:1:tests@jar'
        'a'   | 'a'  | null    | 'tests'    | 'jar' | 'a:a::tests@jar'
        'a'   | 'a'  | '1'     | 'tests'    | null  | 'a:a:1:tests'
        'a'   | 'a'  | '1'     | null       | null  | 'a:a:1'
        'a'   | 'a'  | null    | null       | null  | 'a:a'
        null  | 'a'  | null    | null       | null  | ':a'
    }
}
