package com.netflix.nebula.lint.rule

import spock.lang.Specification
import spock.lang.Unroll

class GradleDependencySpec extends Specification {
    @Unroll
    def 'serialize to String notation `#expected`'() {
        when:
        def dep = new GradleDependency(group, name, version, classifier, ext, null, GradleDependency.Syntax.StringNotation)
        
        then:
        dep.toNotation() == expected
        
        where:
        group   | name  | version   | classifier    | ext   | expected
        'a'     | 'a'   | '1'       | 'tests'       | 'jar' | 'a:a:1:tests@jar'
        'a'     | 'a'   | '1'       | 'tests'       | null  | 'a:a:1:tests'
        'a'     | 'a'   | '1'       | null          | null  | 'a:a:1'
        'a'     | 'a'   | null      | null          | null  | 'a:a'
        null    | 'a'   | null      | null          | null  | ':a'
    }
}
