package com.netflix.nebula.lint.rule.dependency


import nebula.test.IntegrationSpec
import spock.lang.Subject

@Subject(MultiProjectCircularDependencyRule)
class MultiProjectCircularDependencyRuleSpec extends IntegrationSpec {

    def 'No project dependencies'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        !result.standardOutput.contains("Multi-project circular dependencies are not allowed.")
    }

    def 'detects circular dependencies in multi project'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 compile project(':bar')
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                compile project(':foo')
                compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        result.standardOutput.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
    }

    def 'detects circular dependencies in multi project - with excludes'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 compile(project(':bar')) {
                    exclude group: 'org.unwanted', module: 'x'
                 }
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                compile project(':foo')
                compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        result.standardOutput.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
    }


    def 'detects circular dependencies in multi project - multiple dependencies'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 compile 'com.github.zafarkhaja:java-semver:latest.release\'
                 compile 'com.google.guava:guava:20.0\'
                 compile 'com.netflix.nebula:nebula-core:latest.release\'
                 compile 'commons-lang:commons-lang:latest.release\'
                 compile 'joda-time:joda-time:latest.release\'
                 compile 'org.ajoberstar:gradle-git:1.4.+\'        
                 compile project(':bar')
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                 compile 'com.github.zafarkhaja:java-semver:latest.release\'
                 compile 'com.google.guava:guava:20.0\'
                 compile 'com.netflix.nebula:nebula-core:latest.release\'
                 compile 'commons-lang:commons-lang:latest.release\'
                 compile 'joda-time:joda-time:latest.release\'
                 compile 'org.ajoberstar:gradle-git:1.4.+'   
                 compile project(':foo')
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        result.standardOutput.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
    }

    def 'detects circular dependencies in multi project - multi violations'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 compile project(':bar')
                 compile project(':baz')
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                compile project(':foo')
                compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())
        addSubproject('baz', '''\
            apply plugin: 'java'
            dependencies {
                compile project(':foo')
                compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksSuccessfully('fixGradleLint')

        then:
        result.standardOutput.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'bar'")
        result.standardOutput.contains("Multi-project circular dependencies are not allowed. Circular dependency found between projects 'foo' and 'baz'")
    }

    def 'detects circular dependencies in multi project - no parenthesis results in gradle failure but not lint'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 compile project ':bar'
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                compile project ':foo'
                compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksWithFailure('fixGradleLint')

        then:
        result.standardError.contains("Could not get unknown property ':foo' for DefaultProjectDependency{dependencyProject='project ':bar'', configuration='default'}")
    }

    def 'detects circular dependencies in multi project - with bad syntax excludes fails but not in lint'() {
        setup:
        buildFile << """
            plugins {
                id 'java'
            }
            apply plugin: 'nebula.lint'

            gradleLint.rules = ['multiproject-circular-dependency']    
        """
        addSubproject('foo', '''\
            apply plugin: 'java'
            dependencies {
                 compile project(':bar') {
                    exclude group: 'org.unwanted', module: 'x'
                 }
                 compile group: 'com.google.guava', name: 'guava', version: '23.5-jre'
            }
            '''.stripIndent())
        addSubproject('bar', '''\
            apply plugin: 'java'
            dependencies {
                compile project(':foo')
                compile group: 'com.google.guava', name: 'guava', version: '23.5-jre' 
            }
            '''.stripIndent())


        when:
        def result = runTasksWithFailure('fixGradleLint')

        then:
        !result.standardError.contains("Could not get unknown property ':foo' for DefaultProjectDependency{dependencyProject='project ':bar'', configuration='default'}")
    }
}
