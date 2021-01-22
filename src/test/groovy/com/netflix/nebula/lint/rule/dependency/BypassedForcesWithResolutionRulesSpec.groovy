/**
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule.dependency

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.dependencies.ModuleBuilder
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf
import spock.lang.Subject
import spock.lang.Unroll

@Subject(BypassedForcesRule)
class BypassedForcesWithResolutionRulesSpec extends IntegrationTestKitSpec {
    File rulesJsonFile
    File mavenrepo

    def setup() {
        setupDependenciesAndRules()
        debug = true
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    @Unroll
    def 'direct dependency force is honored - force to good version while substitution is triggered by a transitive dependency | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << """\
            dependencies {
                implementation('test.nebula:a:1.1.0') {
                    force = true
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // force to an okay version is the primary contributor; the substitution rule was a secondary contributor
        results.output.contains 'test.nebula:a:1.2.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.1.0\n'

        results.output.contains 'aligned'
        results.output.contains '- Forced'

        results.output.contains('0 violations')

        where:
        coreAlignment << [false, true]
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    @Unroll
    def 'direct dependency force not honored - force to bad version triggers a substitution | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << directDependencyForceNotHonoredDependenciesBlock()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)

        where:
        coreAlignment << [true]
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    def 'direct dependency force not honored - multiproject with definitions in parent file in allprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            allprojects {
                apply plugin: 'java'
                ${directDependencyForceNotHonoredDependenciesBlock()}
            }
            project(':sub1') {}
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): $moduleName, sub1\n")
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    def 'direct dependency force not honored - multiproject with definitions in parent file in subprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
                ${directDependencyForceNotHonoredDependenciesBlock()}
            }
            project(':sub1') {}
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    def 'direct dependency force not honored - multiproject with definitions in parent file in subproject definition block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            project(':sub1') {
                ${directDependencyForceNotHonoredDependenciesBlock()}
            }
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    def 'direct dependency force not honored - multiproject with definitions in subproject file'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            """.stripIndent()
        addSubproject('sub1', directDependencyForceNotHonoredDependenciesBlock())

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    @Unroll
    def 'direct dependency force with dependencies as #type show 0 violations | core alignment #coreAlignment'() {
        // note: 'accept' for substitution rules does not match on dynamic versions

        setupSingleProjectBuildFile()
        buildFile << """\
            ext {
                testNebulaVersion = '1.2.0'
            } 
            dependencies {
                implementation("test.nebula:a:$definition") {
                    force = true
                }
                implementation 'test.nebula:a:1.0.0' // added for alignment
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        results.output.contains('0 violations')

        where:
        type             | definition              | coreAlignment
        'major.+'        | '1.+'                   | true
        'latest.release' | 'latest.release'        | true
        'range'          | '[1.0.0,1.2.0]'         | true
        'variable'       | '\${testNebulaVersion}' | true
    }

    @IgnoreIf({ GradleVersion.current().baseVersion >= GradleVersion.version("7.0")})
    def 'works with Groovy 2.4.x - direct dependency force not honored'() {
        def coreAlignment = true
        setupSingleProjectBuildFileForGradle4_10_2()
        buildFile << directDependencyForceNotHonoredDependenciesBlock()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
    }

    @Unroll
    def 'resolution strategy force is honored - force to good version while substitution is triggered by a transitive dependency | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << """\
            configurations.all {
                resolutionStrategy {
                    force 'test.nebula:a:1.1.0'
                }
            }
            dependencies {
                implementation 'test.nebula:a:1.1.0'
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // force to an okay version is the primary contributor; the substitution rule was a secondary contributor
        results.output.contains 'test.nebula:a:1.2.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:b:1.0.0 -> 1.1.0\n'
        results.output.contains 'test.nebula:c:1.0.0 -> 1.1.0\n'

        results.output.contains('0 violations')

        where:
        coreAlignment << [false]
    }

    @Unroll
    def 'resolution strategy force not honored - force to bad version triggers a substitution | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << """\
            ${resolutionStrategyForceNotHonoredConfigurationsBlock()}
            dependencies {
                implementation 'test.nebula:a:1.2.0' // bad version
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)

        where:
        coreAlignment << [true]
    }

    def 'resolution strategy force not honored - multiproject with definitions in parent file in allprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            allprojects {
                apply plugin: 'java'
                ${resolutionStrategyForceNotHonoredConfigurationsBlock()}
                dependencies {
                    implementation 'test.nebula:a:1.2.0' // bad version
                    implementation 'test.nebula:b:1.0.0' // added for alignment
                    implementation 'test.nebula:c:1.0.0' // added for alignment
                }
            }
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): ${moduleName}, sub1\n")
    }

    def 'resolution strategy force not honored - multiproject with definitions in parent file in subprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
                ${resolutionStrategyForceNotHonoredConfigurationsBlock()}
                dependencies {
                    implementation 'test.nebula:a:1.2.0' // bad version
                    implementation 'test.nebula:b:1.0.0' // added for alignment
                    implementation 'test.nebula:c:1.0.0' // added for alignment
                }
            }
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'resolution strategy force not honored - multiproject with definitions in parent file in subproject definition block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            project(':sub1') {
                ${resolutionStrategyForceNotHonoredConfigurationsBlock()}
                dependencies {
                    implementation 'test.nebula:a:1.2.0' // bad version
                    implementation 'test.nebula:b:1.0.0' // added for alignment
                    implementation 'test.nebula:c:1.0.0' // added for alignment
                }
            }
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'resolution strategy force not honored - multiproject with definitions in subproject file'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            """.stripIndent()
        addSubproject('sub1', """
            ${resolutionStrategyForceNotHonoredConfigurationsBlock()}
            dependencies {
                implementation 'test.nebula:a:1.2.0' // bad version
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }""".stripIndent())

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'resolution strategy force not honored - multiproject with force in parent file and dependencies in subproject file'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
                ${resolutionStrategyForceNotHonoredConfigurationsBlock()}
                task dependencyInsightForAll(type: DependencyInsightReportTask) {}
            }
            """.stripIndent()
        addSubproject('sub1', """
            dependencies {
                implementation 'test.nebula:a:1.2.0' // bad version
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }""".stripIndent())
        addSubproject('sub2', """
            dependencies {
                implementation 'test.nebula:a:1.0.0'
            }""".stripIndent())

        when:
        def tasks = ['dependencyInsightForAll', '--dependency', 'test.nebula', '--configuration', 'compileClasspath', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")

        assert results.output.contains("""> Task :sub2:dependencyInsightForAll
test.nebula:a:1.2.0\n""")
    }

    def 'works with Groovy 2.4.x - resolution strategy force not honored'() {
        def coreAlignment = true
        setupSingleProjectBuildFileForGradle4_10_2()
        buildFile << """\
            ${resolutionStrategyForceNotHonoredConfigurationsBlock()}
            dependencies {
                implementation 'test.nebula:a:1.2.0' // bad version
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assertDirectDependencyAndResolutionStrategyForceNotHonored(results.output)
    }

    @Unroll
    def 'handles multiple forces in one statement | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << """\
            configurations.all {
                resolutionStrategy {
                    force 'test.foo:bar:1.2.0', 
                        'test.nebula:a:1.2.0' 
                        
                }
            }
            dependencies {
                implementation 'test.nebula:a:1.2.0' // bad version
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.foo:bar:1.0.0'
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule('test.foo:bar:1.0.0')
                .addModule('test.foo:bar:1.2.0')
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test', '--warning-mode', 'none', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version was the primary contributor; force to a bad version was a secondary contributor
        assert results.output.contains('test.nebula:a:1.2.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:b:1.0.0 -> 1.3.0\n')
        assert results.output.contains('test.nebula:c:1.0.0 -> 1.3.0\n')
        assert results.output.contains('test.foo:bar:1.0.0 -> 1.2.0\n')

        assert results.output.contains('This project contains lint violations.')
        assert results.output.contains('bypassed-forces')
        assert results.output.contains('The dependency force has been bypassed')

        // the force in a multiple force declaration statement
        assert results.output.contains("""build.gradle:17
'test.nebula:a:1.2.0'""")

        results.output.contains 'aligned'
        results.output.contains('- Forced')

        where:
        coreAlignment << [true]
    }

    def 'handles dependencies and forces defined per project | core alignment #coreAlignment'() {
        definePluginOutsideOfPluginBlock = true

        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:d:1.0.0')
                .addModule('test.nebula:d:1.1.0')
                .addModule('test.nebula:d:1.2.0')
                .addModule('test.nebula:d:1.3.0')

                .addModule('test.nebula:e:1.0.0')
                .addModule('test.nebula:e:1.1.0')
                .addModule('test.nebula:e:1.2.0')
                .addModule('test.nebula:e:1.3.0')
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        buildFile << """\
            allprojects {
                apply plugin: 'nebula.lint'
                repositories {
                    maven { url '${mavenrepo.absolutePath}' }
                }
                gradleLint.rules = ['bypassed-forces']
            }
            subprojects {
                apply plugin: 'java'
                dependencies {
                    implementation (group: 'test.nebula', name: 'a', version: 'latest.release') { force = true }
                }
                task dependenciesForAll(type: DependencyReportTask) {}
            }
            project(':sub2') {
                apply plugin: 'java'
                dependencies {
                    implementation (group: 'test.nebula', name: 'b', version: 'latest.release') { force = true }
                }
            }
            // this style is never seen by lint
            [project(':sub1'), project(':sub2')].each { project ->
                project.apply plugin: 'java'
                project.dependencies {
                    implementation (group: 'test.nebula', name: 'c', version: 'latest.release') { force = true }
                }
            }
            """.stripIndent()
        addSubproject('sub1', """
        configurations {
            newConfiguration1
        }
        dependencies {
            implementation (group: 'test.nebula', name: 'd', version: '1.2.0') { force = true }
        }
        """.stripIndent())

        addSubproject('sub2', """
        configurations {
            newConfiguration2
        }
        dependencies {
            implementation (group: 'test.nebula', name: 'e', version: '1.1.0') { force = true }
        }
        """.stripIndent())

        when:
        def tasks = ['dependenciesForAll', '--configuration', 'compileClasspath', '--warning-mode', 'none']
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        !results.output.contains('FAILED')
    }

    @Unroll
    def 'resolution strategy force with dependencies as #type show 0 violations | core alignment #coreAlignment'() {
        // note: 'accept' for substitution rules does not match on dynamic versions

        setupSingleProjectBuildFile()
        buildFile << """\
            ext {
                testNebulaVersion = '1.+'
            }
            configurations.all {
                resolutionStrategy {
                    force "test.nebula:a:$definition"
                }
            }
            dependencies {
                implementation 'test.nebula:a:1.0.0' // added for alignment
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        results.output.contains('0 violations')

        where:
        type             | definition              | coreAlignment
        'major.+'        | '1.+'                   | true
        'latest.release' | 'latest.release'        | true
        'range'          | '[1.0.0,1.2.0]'         | true
        'variable'       | '\${testNebulaVersion}' | true
    }

    @Unroll
    def 'dependency with strict version declaration honored | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << """\
            dependencies {
                implementation('test.nebula:a') {
                    version { strictly '1.1.0' }
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
                implementation 'test.other:z:1.0.0' // brings in bad version
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // strictly rich version constraint to an okay version is the primary contributor
        results.output.contains('test.nebula:a:{strictly 1.1.0} -> 1.1.0\n')
        results.output.contains('test.nebula:a:1.2.0 -> 1.1.0\n')
        results.output.contains('test.nebula:b:1.0.0 -> 1.1.0\n')
        results.output.contains('test.nebula:c:1.0.0 -> 1.1.0\n')

        results.output.contains('- Forced')
        results.output.contains 'aligned'

        results.output.contains('0 violations')

        where:
        coreAlignment << [true]
    }

    @Unroll
    def 'dependency with strict version declaration not honored | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << strictVersionsDeclarationNotHonoredDependenciesBlock()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)

        where:
        coreAlignment << [false, true]
    }

    def 'dependency with strict version declaration not honored | multiproject with definitions in parent file in allprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            allprojects {
                apply plugin: 'java'
                ${strictVersionsDeclarationNotHonoredDependenciesBlock()}
            }
            project(':sub1') {}
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): $moduleName, sub1\n")
    }

    def 'dependency with strict version declaration not honored | multiproject with definitions in parent file in subprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
                ${strictVersionsDeclarationNotHonoredDependenciesBlock()}
            }
            project(':sub1') {}
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'dependency with strict version declaration not honored | multiproject with definitions in parent file in subproject definition block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            project(':sub1') {
                ${strictVersionsDeclarationNotHonoredDependenciesBlock()}
            }
            """.stripIndent()
        addSubproject('sub1')

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'dependency with strict version declaration not honored | multiproject with definitions in subproject file'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            project(':sub1') {}
            """.stripIndent()
        addSubproject('sub1', strictVersionsDeclarationNotHonoredDependenciesBlock())

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    @Unroll
    def 'dependency with strict version declaration with dependencies as #type show 0 violations | core alignment #coreAlignment'() {
        // note: 'accept' for substitution rules does not match on dynamic versions

        setupSingleProjectBuildFile()
        buildFile << """\
            ext {
                testNebulaVersion = '1.+'
            } 
            dependencies {
                implementation('test.nebula:a') {
                    version { strictly "$definition" }
                }
                implementation 'test.nebula:b:1.0.0' // added for alignment
                implementation 'test.nebula:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        results.output.contains('0 violations')

        where:
        type             | definition              | coreAlignment
        'major.+'        | '1.+'                   | true
        'latest.release' | 'latest.release'        | true
        'range'          | '[1.0.0,1.2.0]'         | true
        'variable'       | '\${testNebulaVersion}' | true
    }

    def 'works with Groovy 2.4.x - dependency with strict version declaration not honored'() {
        def coreAlignment = true
        setupSingleProjectBuildFileForGradle4_10_2()
        buildFile << strictVersionsDeclarationNotHonoredDependenciesBlock()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        results.output.contains('BUILD SUCCESSFUL')
    }

    @Unroll
    def 'dependency constraint with strict version declaration honored | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << """\
            dependencies {
                constraints {
                    implementation('test.nebula:a') {
                        version { strictly("1.1.0") }
                        because '☘︎ custom constraint: test.nebula:a should be 1.1.0'
                    }
                }
                implementation 'test.other:z:1.0.0' // brings in bad version
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // strictly rich version constraint to an okay version is the primary contributor
        results.output.contains('test.nebula:a:{strictly 1.1.0} -> 1.1.0\n')
        results.output.contains('test.nebula:a:1.2.0 -> 1.1.0\n')
        results.output.contains('test.nebula:b:1.0.0 -> 1.1.0\n')
        results.output.contains('test.nebula:c:1.0.0 -> 1.1.0\n')

        results.output.contains 'aligned'

        results.output.contains('0 violations')

        where:
        coreAlignment << [false, true]
    }

    @Unroll
    def 'dependency constraint with strict version declaration not honored | core alignment #coreAlignment'() {
        setupSingleProjectBuildFile()
        buildFile << """\
            dependencies {
                ${dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock()}                
                implementation 'test.brings-a:a:1.0.0' // added for alignment
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)

        where:
        coreAlignment << [false, true]
    }

    def 'dependency constraint with strict version declaration not honored | multiproject with definitions in parent file in allprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            allprojects {
                apply plugin: 'java'
                dependencies {
                    ${dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock()}                
                    implementation 'test.brings-a:a:1.0.0' // added for alignment
                    implementation 'test.brings-b:b:1.0.0' // added for alignment
                    implementation 'test.brings-c:c:1.0.0' // added for alignment
                }
            }
            """.stripIndent()
        addSubproject('sub1')

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): $moduleName, sub1\n")
    }

    def 'dependency constraint with strict version declaration not honored | multiproject with definitions in parent file in subprojects block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
                dependencies {
                    ${dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock()}                
                    implementation 'test.brings-a:a:1.0.0' // added for alignment
                    implementation 'test.brings-b:b:1.0.0' // added for alignment
                    implementation 'test.brings-c:c:1.0.0' // added for alignment
                }
            }
            """.stripIndent()
        addSubproject('sub1')

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'dependency constraint with strict version declaration not honored | multiproject with definitions in parent file in subproject definition block'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            project(':sub1') {
                dependencies {
                    ${dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock()}                
                    implementation 'test.brings-a:a:1.0.0' // added for alignment
                    implementation 'test.brings-b:b:1.0.0' // added for alignment
                    implementation 'test.brings-c:c:1.0.0' // added for alignment
                }
            }
            """.stripIndent()
        addSubproject('sub1')

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'dependency constraint with strict version declaration not honored | multiproject with definitions in subproject file'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
            }
            """.stripIndent()
        addSubproject('sub1', """
            dependencies {
                ${dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock()}                
                implementation 'test.brings-a:a:1.0.0' // added for alignment
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }""".stripIndent())

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = [':sub1:dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1\n")
    }

    def 'dependency constraint with strict version declaration not honored | multiproject with force in parent file and dependencies in subproject file'() {
        def coreAlignment = true

        setupMultiProjectBuildFile()
        buildFile << """\
            subprojects {
                apply plugin: 'java'
                dependencies {
                    ${dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock()}
                }
                task dependencyInsightForAll(type: DependencyInsightReportTask) {}
            }
            """.stripIndent()
        addSubproject('sub1', """
            dependencies {
                implementation 'test.brings-a:a:1.0.0' // added for alignment
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }""".stripIndent())
        addSubproject('sub2', """
            dependencies {
                implementation 'test.brings-a:a:1.0.0'
            }""".stripIndent())

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsightForAll', '--dependency', 'test.nebula', '--configuration', 'compileClasspath', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        // substitution rule to a known-good-version is the primary contributor; rich version strictly constraint to a bad version is the secondary contributor
        assertStrictVersionsDeclarationNotHonored(results.output)
        assert results.output.contains("Remove or update this value for the affected project(s): sub1, sub2\n")

        assert results.output.contains("""> Task :sub2:dependencyInsightForAll
test.nebula:a:1.3.0\n""")
    }

    @Unroll
    def 'dependency constraint with strict version declaration with dependencies as #type show 0 violations | core alignment #coreAlignment'() {
        // note: 'accept' for substitution rules does not match on dynamic versions

        setupSingleProjectBuildFile()
        buildFile << """\
            ext {
                testNebulaVersion = '1.+'
            } 
            dependencies {
                constraints {
                    implementation('test.nebula:a') {
                        version { strictly("$definition") }
                        because "☘︎ custom constraint: test.nebula:a should be $definition"
                    }
                }
                implementation 'test.brings-a:a:1.0.0' // added for alignment
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        results.output.contains('0 violations')

        where:
        type             | definition              | coreAlignment
        'major.+'        | '1.+'                   | true
        'latest.release' | 'latest.release'        | true
        'range'          | '[1.0.0,1.2.0]'         | true
        'variable'       | '\${testNebulaVersion}' | true
    }

    def 'works with Groovy 2.4.x - dependency constraint with strict version declaration not honored'() {
        def coreAlignment = true
        setupSingleProjectBuildFileForGradle4_10_2()
        buildFile << """\
            dependencies {
                ${dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock()}                
                implementation 'test.brings-a:a:1.0.0' // added for alignment
                implementation 'test.brings-b:b:1.0.0' // added for alignment
                implementation 'test.brings-c:c:1.0.0' // added for alignment
            }
        """.stripIndent()

        def graph = new DependencyGraphBuilder()
                .addModule(new ModuleBuilder('test.brings-b:b:1.0.0').addDependency('test.nebula:b:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-a:a:1.0.0').addDependency('test.nebula:a:1.0.0').build())
                .addModule(new ModuleBuilder('test.brings-c:c:1.0.0').addDependency('test.nebula:c:1.0.0').build())
                .build()
        new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        when:
        def tasks = ['dependencyInsight', '--dependency', 'test.nebula', "-Dnebula.features.coreAlignmentSupport=$coreAlignment"]
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        results.output.contains('BUILD SUCCESSFUL')
    }

    @Unroll
    def 'ignores buildscript dependencies for #type'() {
        buildFile << """\
            buildscript {
                repositories {
                    maven { url "https://plugins.gradle.org/m2/" }
                }
                dependencies {
                    classpath("com.netflix.nebula:nebula-dependency-recommender:9.0.2") {
                        force = true
                    }
                }
            }
            plugins {
                id 'java'
                id 'nebula.lint'
            }
            apply plugin: "nebula.dependency-recommender"
            gradleLint.rules = ['bypassed-forces']
        """.stripIndent()

        when:
        def tasks = ['buildEnvironment', 'dependencies', '--warning-mode', 'none']
        tasks += 'fixGradleLint'
        def results = runTasks(*tasks)

        then:
        results.output.contains('0 violations')

        where:
        type                      | _
        'direct dependency force' | _
    }

    private void setupDependenciesAndRules() {
        def graph = new DependencyGraphBuilder()
                .addModule('test.nebula:a:1.0.0')
                .addModule('test.nebula:a:1.1.0')
                .addModule('test.nebula:a:1.2.0')
                .addModule('test.nebula:a:1.3.0')

                .addModule('test.nebula:b:1.0.0')
                .addModule('test.nebula:b:1.1.0')
                .addModule('test.nebula:b:1.2.0')
                .addModule('test.nebula:b:1.3.0')

                .addModule('test.nebula:c:1.0.0')
                .addModule('test.nebula:c:1.1.0')
                .addModule('test.nebula:c:1.2.0')
                .addModule('test.nebula:c:1.3.0')

                .addModule(new ModuleBuilder('test.other:z:1.0.0').addDependency('test.nebula:a:1.2.0').build())

                .build()
        mavenrepo = new GradleDependencyGenerator(graph, "${projectDir}/testrepogen").generateTestMavenRepo()

        rulesJsonFile = new File(projectDir, "${moduleName}.json")
        String reason = "★ custom substitution reason"
        rulesJsonFile << """
            {
                "substitute": [
                    {
                        "module": "test.nebula:a:1.2.0",
                        "with": "test.nebula:a:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "module": "test.nebula:b:1.2.0",
                        "with": "test.nebula:b:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    },
                    {
                        "module": "test.nebula:c:1.2.0",
                        "with": "test.nebula:c:1.3.0",
                        "reason": "$reason",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ],
                "align": [
                    {
                        "group": "test.nebula",
                        "reason": "Align test.nebula dependencies",
                        "author": "Example Person <person@example.org>",
                        "date": "2016-03-17T20:21:20.368Z"
                    }
                ]
            }
            """.stripIndent()
    }

    private void setupSingleProjectBuildFile() {
        buildFile << """\
            plugins {
                id 'java'
                id "nebula.resolution-rules" version "7.5.0" 
                id 'nebula.lint'
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            gradleLint.rules = ['bypassed-forces']
            """.stripIndent()
    }

    private void setupSingleProjectBuildFileForGradle4_10_2() {
        gradleVersion = '4.10.2' // with groovy 2.4.15

        buildFile << """
            plugins {
                id 'java'
                id "nebula.resolution-rules" version "6.0.5" 
                id 'nebula.lint'
            }

            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
            repositories {
                maven { url '${mavenrepo.absolutePath}' }
            }
            gradleLint.rules = ['bypassed-forces']
            """
    }

    private void setupMultiProjectBuildFile() {
        definePluginOutsideOfPluginBlock = true

        buildFile << """\
            buildscript {
                repositories { maven { url "https://plugins.gradle.org/m2/" } }
                dependencies {
                    classpath "com.netflix.nebula:gradle-resolution-rules-plugin:7.5.0"
                }
            }
            allprojects {
                apply plugin: 'nebula.lint'
                apply plugin: 'nebula.resolution-rules'
                repositories {
                    maven { url '${mavenrepo.absolutePath}' }
                }
                gradleLint.rules = ['bypassed-forces']
            }
            dependencies {
                resolutionRules files('$rulesJsonFile')
            }
            """.stripIndent()
    }

    private static String directDependencyForceNotHonoredDependenciesBlock() {
        """
        dependencies {
            implementation('test.nebula:a:1.2.0') {
                force = true // force to bad version triggers a substitution
            }
            implementation 'test.nebula:b:1.0.0' // added for alignment
            implementation 'test.nebula:c:1.0.0' // added for alignment
        }
        """.stripIndent()
    }

    private static String resolutionStrategyForceNotHonoredConfigurationsBlock() {
        """
        configurations.all {
            resolutionStrategy {
                force 'test.nebula:a:1.2.0' // force to bad version triggers a substitution
            }
        }
        """.stripIndent()
    }

    private static String strictVersionsDeclarationNotHonoredDependenciesBlock() {
        """
        dependencies {
            implementation('test.nebula:a') {
                version { strictly '1.2.0' } // strict to bad version
            }
            implementation 'test.nebula:b:1.0.0' // added for alignment
            implementation 'test.nebula:c:1.0.0' // added for alignment
        }""".stripIndent()
    }

    private static String dependencyConstraintWithStrictVersionDeclarationNotHonoredDependenciesBlock() {
        """
        constraints {
            implementation('test.nebula:a') {
                version { strictly("1.2.0") }
                because '☘︎ custom constraint: test.nebula:a should be 1.2.0'
            }
        }""".stripIndent()
    }

    private static void assertDirectDependencyAndResolutionStrategyForceNotHonored(String output) {
        assert output.contains('test.nebula:a:1.2.0 -> 1.3.0\n')
        assert output.contains('test.nebula:b:1.0.0 -> 1.3.0\n')
        assert output.contains('test.nebula:c:1.0.0 -> 1.3.0\n')

        assert output.contains('This project contains lint violations.')
        assert output.contains('bypassed-forces')
        assert output.findAll('bypassed-forces').size() == 1
        assert output.contains('The dependency force has been bypassed')

        assert output.contains('aligned')
        assert output.contains('- Forced')
    }

    private static void assertStrictVersionsDeclarationNotHonored(String output) {
        assert output.contains('test.nebula:a:{strictly 1.2.0} -> 1.3.0\n')
        assert output.contains('test.nebula:b:1.0.0 -> 1.3.0\n')
        assert output.contains('test.nebula:c:1.0.0 -> 1.3.0\n')

        assert output.contains('aligned')

        assert output.contains('This project contains lint violations.')

        assert output.contains('bypassed-forces')
        assert output.findAll('bypassed-forces').size() == 1
        assert output.contains('The strict version constraint has been bypassed')

    }
}
