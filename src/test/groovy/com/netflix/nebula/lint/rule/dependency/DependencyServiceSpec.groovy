package com.netflix.nebula.lint.rule.dependency


import nebula.test.IntegrationTestKitSpec
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.DefaultResolvedDependency
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.nebula.lint.rule.dependency.DependencyClassVisitorSpec.gav

@Subject(DependencyService)
class DependencyServiceSpec extends IntegrationTestKitSpec {
    Project project

    def setup() {
        project = ProjectBuilder.builder().withName('dependency-service').withProjectDir(projectDir).build()
        project.with {
            apply plugin: 'java'
            repositories { mavenCentral() }
        }
    }

    def 'check if configuration is resolved'() {
        setup:
        project.apply plugin: 'war'
        project.dependencies {
            implementation 'com.google.guava:guava:19.0'
            providedCompile 'commons-lang:commons-lang:latest.release'
        }

        when:
        def service = DependencyService.forProject(project)
        project.configurations.compileClasspath.resolve()

        then:
        service.isResolved('implementation')
        service.isResolved('providedCompile')
    }

    def 'transitive dependencies with a cycle'() {
        setup:
        def service = DependencyService.forProject(project)

        def resolvedDependency = { String dep ->
            def (group, name, version) = dep.split(':')
            gav(group, name, version)
        }

        when:
        DefaultResolvedDependency a1 = resolvedDependency('a:a:1')
        DefaultResolvedDependency b1 = resolvedDependency('b:b:1')
        a1.addChild(b1)
        b1.addChild(a1)

        def transitives = service.transitiveDependencies(a1)

        then:
        transitives == [b1] as Set
    }

    @Unroll
    def 'find unused dependencies'() {
        when:
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'nebula.lint'
                id 'java'
            }

            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.inject.extensions:guice-servlet:3.0' // used directly
                implementation 'javax.servlet:servlet-api:2.5' // used indirectly through guice-servlet
                implementation 'commons-lang:commons-lang:2.6' // unused
                testImplementation 'junit:junit:4.11'
                testImplementation 'commons-lang:commons-lang:2.6' // unused
            }

            // a task to generate an unused dependency report for each configuration
            [implementation: 'compileClasspath', testImplementation: 'testCompileClasspath'].each { declaredConf, resolvedConf ->
                task "\${declaredConf}Unused"(dependsOn: compileTestJava) {
                    doLast {
                        new File(projectDir, "\${declaredConf}Unused.txt").text = DependencyService.forProject(project)
                        .unusedDependencies(resolvedConf, declaredConf)
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        writeJavaSourceFile('''\
            import com.google.inject.servlet.*;
            public abstract class Main extends GuiceServletContextListener { }
        ''')

        writeUnitTest()

        then:
        runTasks('implementationUnused')
        new File(projectDir, 'implementationUnused.txt').readLines() == ['commons-lang:commons-lang']

        then:
        runTasks('testImplementationUnused')
        new File(projectDir, 'testImplementationUnused.txt').readLines() == ['commons-lang:commons-lang']
    }

    @Unroll
    def 'find undeclared dependencies'() {
        when:
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'nebula.lint'
                id 'java'
            }

            repositories { mavenCentral() }
            dependencies {
                implementation 'io.springfox:springfox-core:2.0.2'
            }

            // a task to generate an undeclared dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}Undeclared"(dependsOn: compileTestJava) {
                    doLast {
                         new File(projectDir, "\${conf}Undeclared.txt").text = DependencyService.forProject(project)
                        .undeclaredDependencies(conf)
                        .join('\\n')
                    }
                }
            }
            """.stripMargin()

        writeJavaSourceFile('''\
            import com.google.common.collect.*;
            public class Main { Multimap m = HashMultimap.create(); }
        ''')

        then:
        runTasks('compileClasspathUndeclared')
        new File(projectDir, 'compileClasspathUndeclared.txt').readLines() == ['com.google.guava:guava:18.0']
    }

    def 'first level dependencies in conf'() {
        when:
        project.dependencies {
            implementation 'com.google.guava:guava:18.0'
            testImplementation 'junit:junit:latest.release'
        }

        def deps = DependencyService.forProject(project).firstLevelDependenciesInConf(project.configurations.testCompileClasspath, 'testImplementation')

        project.configurations.compileClasspath.incoming.afterResolve {
            project.configurations.compileClasspath.incoming.resolutionResult.root.dependencies
        }

        then:
        deps.size() == 1
        deps[0].module.toString() == 'junit:junit'
        deps[0].version != 'latest.release' // the version has been resolved to a fixed version
    }

    def 'find the nearest source set to a configuration'() {
        when:
        project.with {
            apply plugin: 'war' // to define the providedCompile conf
            configurations {
                deeper
                deep { extendsFrom deeper }
            }
            configurations.implementation { extendsFrom configurations.deep }
        }

        def service = DependencyService.forProject(project)

        then:
        service.sourceSetByConf('implementation')?.name == 'main'
        service.sourceSetByConf('providedCompile')?.name == 'main'
        service.sourceSetByConf('deeper')?.name == 'main'
    }

    @Issue('39')
    def 'compile sourceSet is not mixed up with integTest class output'() {
        setup:
        buildFile.text = """
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.integtest' version '10.1.4'
                id 'nebula.lint'
            }
            
            task compileClasspathSourceSetOutput {
                doLast {
                  println('@@' + DependencyService.forProject(project).sourceSetByConf('compileClasspath').java.classesDirectory.get())
                }
            }
            
            task integTestSourceSetOutput {
                doLast {
                   println('@@' + DependencyService.forProject(project).sourceSetByConf('integTestCompileClasspath').java.classesDirectory.get())
                }
            }
        """

        when:
        def results = runTasks('compileClasspathSourceSetOutput')
        def dir = results.output.readLines().find { it.startsWith('@@') }.substring(2)

        then:
        dir.split('/')[-1] == 'main'

        when:
        results = runTasks('integTestSourceSetOutput')
        dir = results.output.readLines().find { it.startsWith('@@') }.substring(2)

        then:
        dir.split('/')[-1] == 'integTest'
    }

    def 'identify parent source sets'() {
        expect:
        DependencyService.forProject(project).parentSourceSetConfigurations('implementation')*.name == ['compileClasspath', 'testCompileClasspath']
    }

    @Unroll
    def 'read jar contents of project dependencies'() {
        setup:
        def core = addSubproject('core')
        def web = addSubproject('web')

        buildFile << """
            plugins {
                id 'nebula.lint'
            }

            subprojects {
                apply plugin: 'java'
            }
        """

        new File(web, 'build.gradle').text = """
            import com.netflix.nebula.lint.rule.dependency.*

            dependencies {
                implementation project(':core')
            }

            task coreContents {
                doLast {
                    new File(projectDir, "coreContents.txt").text = DependencyService.forProject(project)
                    .jarContents(configurations.compileClasspath.resolvedConfiguration.firstLevelModuleDependencies[0].module.id.module)
                    .classes
                    .join('\\n')
                }
            }
        """

        writeJavaSourceFile('public class A {}', core)

        when:
        runTasks(*tasks)
        def coreContents = new File(web, 'coreContents.txt')

        then:
        coreContents.readLines() == contents

        where:
        tasks                                | contents
        ['web:coreContents']                 | []
        ['web:assemble', 'web:coreContents'] | ['A']
    }

    def 'resolved configurations returns lists of configurations that are resolvable and resolved'() {
        given:
        definePluginOutsideOfPluginBlock = true
        def dependency = 'commons-lang:commons-lang:latest.release'

        buildFile << """
            apply plugin: 'java-library'

            repositories { mavenCentral() }

            dependencies {
                compile '${dependency}'
                compileOnly '${dependency}'
                testCompile '${dependency}'
                testCompileOnly '${dependency}'

                implementation '${dependency}'
                testImplementation '${dependency}'

                apiElements '${dependency}'

                runtimeElements '${dependency}'

                runtime '${dependency}'
                testRuntime '${dependency}'

                runtimeOnly '${dependency}'
                testRuntimeOnly '${dependency}'
            }
            
            import com.netflix.nebula.lint.rule.dependency.*
            
            task resolvableAndResolvedConfigurations {
                doLast {
                    new File(projectDir, "resolvableAndResolvedConfigurations.txt").text = DependencyService.forProject(project)
                    .resolvableAndResolvedConfigurations()
                    .join('\\n')
                }
            }
            """.stripIndent()

        writeJavaSourceFile('public class Main {}')
        writeUnitTest('public class TestMain {}')

        def dependencyService = DependencyService.forProject(project)
        dependencyService.resolvableConfigurations().each { resolvableConf ->
            resolvableConf.resolve()
        }

        when:
        def resolvedConfigurations = dependencyService.resolvableAndResolvedConfigurations()

        then:
        def configurationNames = resolvedConfigurations.collect { conf -> conf.getName() }

        configurationNames.size() > 0

        // sample the configurations
        configurationNames.contains('compileClasspath')
        configurationNames.contains('testCompileClasspath')

        !configurationNames.contains('compile')
        !configurationNames.contains('testCompile')

        !configurationNames.contains('implementation')
        !configurationNames.contains('testImplementation')

        !configurationNames.contains('compileOnly')
        !configurationNames.contains('testCompileOnly')

        !configurationNames.contains('apiElements')
        !configurationNames.contains('runtimeElements')

        configurationNames.contains('runtimeClasspath')
        configurationNames.contains('testRuntimeClasspath')

        !configurationNames.contains('runtime')
        !configurationNames.contains('testRuntime')

        !configurationNames.contains('runtimeOnly')
        !configurationNames.contains('testRuntimeOnly')

        // and more!
    }

    @Unroll
    def '#confSuffix for custom sourceSets should translate to proper Classpath configuration'() {
        project.sourceSets {
            backend {
                java {
                    compileClasspath += main.output
                    runtimeClasspath += main.output
                }
            }
        }

        when:
        def result = DependencyService.forProject(project)
                .findAndReplaceDeprecatedConfiguration(project.configurations."backend$confSuffix")

        then:
        result.name == "backend$expectedSuffix"

        where:
        confSuffix         | expectedSuffix
        'CompileOnly'      | 'CompileClasspath'
        'RuntimeOnly'      | 'RuntimeClasspath'
        'CompileClasspath' | 'CompileClasspath'
        'RuntimeClasspath' | 'RuntimeClasspath'
        'Implementation'   | 'CompileClasspath'
    }

    @Unroll
    def 'findAndReplaceNonResolvableConfiguration works for #configName'() {
        given:
        project.with {
            apply plugin: 'java-library'
        }
        writeJavaSourceFile('public class Main {}')

        def dependencyService = DependencyService.forProject(project)

        when:
        def resolvableConfig = dependencyService.findAndReplaceNonResolvableConfiguration(project.configurations."$configName")

        then:
        resolvableConfig.name == resolvableConfigName

        where:
        configName           | resolvableConfigName
        'api'                | 'compileClasspath'
        'compileOnly'        | 'compileClasspath'
        'implementation'     | 'compileClasspath'
        'runtimeOnly'        | 'runtimeClasspath'

        'testCompileOnly'    | 'testCompileClasspath'
        'testImplementation' | 'testCompileClasspath'
        'testRuntimeOnly'    | 'testRuntimeClasspath'
    }

    @Unroll
    def 'findAndReplaceNonResolvableConfiguration works for custom source sets #configName'() {
        given:
        project.with {
            apply plugin: 'java-library'
        }
        writeJavaSourceFile('public class Main {}')

        // to verify with custom sourcesets
        project.sourceSets {
            special {
                java {
                    compileClasspath += main.output
                    runtimeClasspath += main.output
                }
            }
        }

        def dependencyService = DependencyService.forProject(project)

        when:
        def resolvableConfig = dependencyService.findAndReplaceNonResolvableConfiguration(project.configurations."$configName")

        then:
        resolvableConfig.name == resolvableConfigName

        where:
        configName              | resolvableConfigName
        'specialCompileOnly'    | 'specialCompileClasspath'
        'specialImplementation' | 'specialCompileClasspath'
        'specialRuntimeOnly'    | 'specialRuntimeClasspath'
    }

    @Unroll
    def 'findAndReplaceNonResolvableConfiguration works for custom configurations #configName'() {
        given:
        project.with {
            apply plugin: 'java-library'

            // to verify with custom configurations
            configurations {
                myNonResolvableConfig {
                    canBeResolved = false
                    canBeConsumed = true
                }
                myNonResolvableConfigWithParent {
                    canBeResolved = false
                    canBeConsumed = true
                }
                myResolvableConfig {
                    canBeResolved = true
                    canBeConsumed = true
                }
                compileClasspath.extendsFrom myNonResolvableConfigWithParent
            }
        }
        writeJavaSourceFile('public class Main {}')

        def dependencyService = DependencyService.forProject(project)

        when:
        def resolvableConfig = dependencyService.findAndReplaceNonResolvableConfiguration(project.configurations."$configName")

        then:
        resolvableConfig.name == resolvableConfigName

        where:
        configName                        | resolvableConfigName
        'myResolvableConfig'              | 'myResolvableConfig'
        'myNonResolvableConfigWithParent' | 'compileClasspath'
        'myNonResolvableConfig'           | 'myNonResolvableConfig' // returns the original config when the resolution alternative is unclear
    }
}