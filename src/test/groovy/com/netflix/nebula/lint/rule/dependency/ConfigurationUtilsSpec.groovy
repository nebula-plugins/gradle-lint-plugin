package com.netflix.nebula.lint.rule.dependency

import nebula.test.AbstractProjectSpec
import static com.netflix.nebula.lint.rule.dependency.ConfigurationUtils.simplify

class ConfigurationUtilsSpec extends AbstractProjectSpec {
    def 'find minimum set of configurations that fully encapsulate a larger set'() {
        when:
        project.apply plugin: 'java'

        def providedConf = project.configurations.create('provided')
        project.configurations.compile.extendsFrom(providedConf)

        project.configurations.create('disjoint')

        then:
        simplify(project, 'testCompile') == ['testCompile'] as Set
        simplify(project, 'compile') == ['compile'] as Set
        simplify(project, 'compile', 'runtime', 'testCompile', 'testRuntime') == ['compile'] as Set
        simplify(project, 'compile', 'testRuntime') == ['compile'] as Set // one or more intermediate configurations between the initial set

        then: "special case: don't simplify to below the compile configuration"
        simplify(project, 'provided') == ['provided'] as Set
        simplify(project, 'provided', 'compile') == ['compile'] as Set

        then: "disjoint configurations"
        simplify(project, 'compile', 'disjoint') == ['compile', 'disjoint'] as Set

        then: "non-existant configurations"
        simplify(project, 'dne') == [] as Set
    }
}
