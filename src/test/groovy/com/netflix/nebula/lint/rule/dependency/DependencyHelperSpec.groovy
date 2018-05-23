package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraphBuilder
import nebula.test.dependencies.GradleDependencyGenerator
import org.codehaus.groovy.ast.expr.MethodCallExpression
import spock.lang.Unroll

class DependencyHelperSpec extends IntegrationSpec {
    @Unroll('should transform(remove) #dep -> #depResult')
    def 'removes version'() {
        given:
        new File(projectDir, 'src/main/resources/META-INF/lint-rules')

        def graph = new DependencyGraphBuilder().addModule('a:b:1.0.0').addModule('test.nebula:foo:1.0.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/myrepo")
        def dir = generator.generateTestMavenRepo()
        new File(dir, 'test/nebula/foo/1.0.0/foo-1.0.0-tests.jar').createNewFile()

        buildFile << """\
            plugins {
                id 'java'
            }
            
            apply plugin: 'nebula.lint'
            
            ext {
                myVersion = '1.0.0'
            }
            
            gradleLint.rules = ['test-dependency-remove-version']
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }
            
            dependencies {
                ${dep}
            }
            """.stripIndent()

        writeHelloWorld('test.nebula')

        expect:
        def results = runTasks('fixGradleLint', '--stacktrace')
        buildFile.text.contains(depResult)
        if (dep != depResult) {
            !buildFile.text.contains(dep)
        }

        where:
        dep | depResult
        'compile \'test.nebula:foo:1.0.0\'' | 'compile \'test.nebula:foo\''
        'testCompile \'test.nebula:foo:1.0.0\'' | 'testCompile \'test.nebula:foo\''
        'compile \'test.nebula:foo:1.0.0:tests\'' | 'compile \'test.nebula:foo::tests\''
        'compile "test.nebula:foo:${myVersion}"' | 'compile "test.nebula:foo"'
        'runtime group: \'test.nebula\', name: \'foo\', version: \'1.0.0\'' | 'runtime group: \'test.nebula\', name: \'foo\''
        'runtime group: \'test.nebula\', name: \'foo\', version: myVersion' | 'runtime group: \'test.nebula\', name: \'foo\''
        'compile \'test.nebula:foo:1.0.0\',\n' + (' ' * 20)  + '\'a:b:1.0.0\'' | 'compile \'test.nebula:foo:1.0.0\',\n' + (' ' * 8) + '\'a:b:1.0.0\'' // opt to ignore lists of strings for now
        'compile(\'test.nebula:foo:1.0.0\') { transitive = false }' | 'compile(\'test.nebula:foo\') { transitive = false }'
        'compile(\'test.nebula:foo:1.0.0\') { }' | 'compile(\'test.nebula:foo\') { }'
        'compile("test.nebula:foo:${myVersion}") { transitive = false }' | 'compile("test.nebula:foo") { transitive = false }'
        'compile(group: \'test.nebula\', name: \'foo\', version: \'1.0.0\') { transitive = false }' | 'compile(group: \'test.nebula\', name: \'foo\') { transitive = false }'
        'compile(\'test.nebula:foo:1.0.0\') { force = true }' | 'compile(\'test.nebula:foo:1.0.0\') { force = true }'
        'compile(group: \'test.nebula\', name: \'foo\', version: \'1.0.0\') { force = true }' | 'compile(group: \'test.nebula\', name: \'foo\', version: \'1.0.0\') { force = true }'
    }

    @Unroll('should transform(replace) #dep -> #depResult')
    def 'replaces version'() {
        given:
        new File(projectDir, 'src/main/resources/META-INF/lint-rules')

        def graph = new DependencyGraphBuilder()
                .addModule('a:b:1.0.0')
                .addModule('a:b:1.1.0')
                .addModule('test.nebula:foo:1.0.0')
                .addModule('test.nebula:foo:1.1.0')
                .build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/myrepo")
        generator.generateTestMavenRepo()

        buildFile << """\
            plugins {
                id 'java'
            }
            
            apply plugin: 'nebula.lint'
            
            ext {
                myVersion = '1.0.0'
            }
            
            gradleLint.rules = ['test-dependency-replace-version']
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }
            
            dependencies {
                ${dep}
            }
            """.stripIndent()

        writeHelloWorld('test.nebula')

        expect:
        def results = runTasks( 'fixGradleLint')
        buildFile.text.contains(depResult)
        if (dep != depResult) {
            !buildFile.text.contains(dep)
        }

        where:
        dep | depResult
        'compile \'test.nebula:foo:1.0.0\'' | 'compile \'test.nebula:foo:1.1.0\''
        'testCompile \'test.nebula:foo:1.0.0\'' | 'testCompile \'test.nebula:foo:1.1.0\''
        'compile "test.nebula:foo:${myVersion}"' | 'compile "test.nebula:foo:${myVersion}"'
        'runtime group: \'test.nebula\', name: \'foo\', version: \'1.0.0\'' | 'runtime group: \'test.nebula\', name: \'foo\', version: \'1.1.0\''
        'runtime group: \'test.nebula\', name: \'foo\', version: myVersion' | 'runtime group: \'test.nebula\', name: \'foo\', version: myVersion'
        'compile \'test.nebula:foo:1.0.0\',\n' + (' ' * 20)  + '\'a:b:1.0.0\'' | 'compile \'test.nebula:foo:1.0.0\',\n' + (' ' * 8) + '\'a:b:1.0.0\'' // opt to ignore lists of strings for now
        'compile(\'test.nebula:foo:1.0.0\') { transitive = false }' | 'compile(\'test.nebula:foo:1.1.0\') { transitive = false }'
        'compile("test.nebula:foo:${myVersion}") { transitive = false }' | 'compile("test.nebula:foo:${myVersion}") { transitive = false }'
        'compile(group: \'test.nebula\', name: \'foo\', version: \'1.0.0\') { transitive = false }' | 'compile(group: \'test.nebula\', name: \'foo\', version: \'1.1.0\') { transitive = false }'
    }

    @Unroll('should replace #dep -> #depResult')
    def 'replaces dependency'() {
        given:
        new File(projectDir, 'src/main/resources/META-INF/lint-rules')

        def graph = new DependencyGraphBuilder().addModule('bar:baz:2.0.0').addModule('test.nebula:foo:1.0.0').build()
        def generator = new GradleDependencyGenerator(graph, "${projectDir}/myrepo")
        generator.generateTestMavenRepo()

        buildFile << """\
            plugins {
                id 'java'
            }
            
            apply plugin: 'nebula.lint'
            
            ext {
                myVersion = '1.0.0'
            }
            
            gradleLint.rules = ['test-dependency-replace']
            
            repositories {
                ${generator.mavenRepositoryBlock}
            }
            
            dependencies {
                ${dep}
            }
            """.stripIndent()

        writeHelloWorld('test.nebula')

        expect:
        def results = runTasks('fixGradleLint')
        buildFile.text.contains(depResult)
        if (dep != depResult) {
            !buildFile.text.contains(dep)
        }

        where:
        dep | depResult
        'compile \'test.nebula:foo:1.0.0\'' | 'compile \'bar:baz:2.0.0\''
        'testCompile \'test.nebula:foo:1.0.0\'' | 'testCompile \'bar:baz:2.0.0\''
        'compile "test.nebula:foo:${myVersion}"' | 'compile "test.nebula:foo:${myVersion}"'
        'runtime group: \'test.nebula\', name: \'foo\', version: \'1.0.0\'' | 'runtime group: \'bar\', name: \'baz\', version: \'2.0.0\''
        'runtime group: \'test.nebula\', name: \'foo\', version: myVersion' | 'runtime group: \'bar\', name: \'baz\', version: \'2.0.0\''
        'compile(\'test.nebula:foo:1.0.0\') { transitive = false }' | 'compile(\'bar:baz:2.0.0\') { transitive = false }'
        'compile("test.nebula:foo:${myVersion}") { transitive = false }' | 'compile("test.nebula:foo:${myVersion}") { transitive = false }'
        'compile(group: \'test.nebula\', name: \'foo\', version: \'1.0.0\') { transitive = false }' | 'compile(group: \'bar\', name: \'baz\', version: \'2.0.0\') { transitive = false }'
    }
}

class TestDependencyRemoveVersionRule extends GradleLintRule implements GradleModelAware {
    String description = "remove all versions"

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def violation = addBuildLintViolation('dep violation', call)
        DependencyHelper.removeVersion(violation, call, dep)
    }
}

class TestDependencyReplaceVersionRule extends GradleLintRule implements GradleModelAware {
    String description = "replace all versions"

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def violation = addBuildLintViolation('dep violation', call)
        DependencyHelper.replaceVersion(violation, call, dep, '1.1.0')
    }
}

class TestDependencyReplaceRule extends GradleLintRule implements GradleModelAware {
    String description = "replace dependency"

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def violation = addBuildLintViolation('need to replace dependency', call)
        DependencyHelper.replaceDependency(violation, call, new GradleDependency('bar', 'baz', '2.0.0'))
    }
}
