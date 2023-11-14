package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Subject
import spock.lang.Unroll

@Subject(DependencyService)
class DependencyServiceWithJavaPlatformSpec extends BaseIntegrationTestKitSpec {
    Project project

    def setup() {
        project = ProjectBuilder.builder().withName('dependency-service').withProjectDir(projectDir).build()
        project.with {
            apply plugin: 'java-platform'
            repositories { mavenCentral() }
        }
    }

    @Unroll
    def 'findAndReplaceNonResolvableConfiguration works with java-platform plugin'() {
        given:
        project.with {

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