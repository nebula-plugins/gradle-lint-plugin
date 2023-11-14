package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import spock.lang.Subject

@Subject(MultiProjectCircularDependencyRule)
class MultiProjectCircularDependencyRuleSpec extends BaseIntegrationTestKitSpec {

    def 'No project dependencies'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
            
            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasks('fixGradleLint')

        then:
        !result.output.contains("Multi-project circular dependencies are not allowed.")
    }

    def 'detects circular dependencies in multi project'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 implementation project(':bar')
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                implementation project(':foo')
                implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasks('fixGradleLint')

        then:
        result.output.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
    }

    def 'detects circular dependencies in multi project - with excludes'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 implementation(project(':bar')) {
                    exclude group: 'org.unwanted', module: 'x'
                 }
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                implementation project(':foo')
                implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasks('fixGradleLint')

        then:
        result.output.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
    }


    def 'detects circular dependencies in multi project - multiple dependencies'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 implementation 'com.github.zafarkhaja:java-semver:latest.release\'
                 implementation 'com.google.guava:guava:20.0\'
                 implementation 'com.netflix.nebula:nebula-core:latest.release\'
                 implementation 'commons-lang:commons-lang:latest.release\'
                 implementation 'joda-time:joda-time:latest.release\'
                 implementation 'org.ajoberstar:gradle-git:1.4.+\'        
                 implementation project(':bar')
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                 implementation 'com.github.zafarkhaja:java-semver:latest.release\'
                 implementation 'com.google.guava:guava:20.0\'
                 implementation 'com.netflix.nebula:nebula-core:latest.release\'
                 implementation 'commons-lang:commons-lang:latest.release\'
                 implementation 'joda-time:joda-time:latest.release\'
                 implementation 'org.ajoberstar:gradle-git:1.4.+'   
                 implementation project(':foo')
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasks('fixGradleLint')

        then:
        result.output.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
    }

    def 'detects circular dependencies in multi project - multi violations'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 implementation project(':bar')
                 implementation project(':baz')
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                implementation project(':foo')
                implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())
        addSubproject('baz', '''\
            apply plugin: 'java'
            dependencies {
                implementation project(':foo')
                implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasks('fixGradleLint')

        then:
        result.output.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
        result.output.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'baz'")
    }

    def 'detects circular dependencies in multi project - no parenthesis results in gradle failure but not lint'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 implementation project ':bar'
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                implementation project ':foo'
                implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksAndFail('fixGradleLint')

        then:
        result.output.contains("Could not get unknown property ':foo' for DefaultProjectDependency{dependencyProject='project ':bar'', configuration='default'}")
    }

    def 'detects circular dependencies in multi project - with bad syntax excludes fails but not in lint'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 implementation project(':bar') {
                    exclude group: 'org.unwanted', module: 'x'
                 }
                 implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                implementation project(':foo')
                implementation group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksAndFail('fixGradleLint')

        then:
        !result.output.contains("Could not get unknown property ':foo' for DefaultProjectDependency{dependencyProject='project ':bar'', configuration='default'}")
    }
}
