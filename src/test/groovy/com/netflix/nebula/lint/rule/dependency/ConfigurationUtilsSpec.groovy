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
