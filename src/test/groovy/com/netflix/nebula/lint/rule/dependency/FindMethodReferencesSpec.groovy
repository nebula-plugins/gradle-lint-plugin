package com.netflix.nebula.lint.rule.dependency

import nebula.test.IntegrationSpec

class FindMethodReferencesSpec extends IntegrationSpec {

    def 'find method references'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
            }

            apply plugin: 'nebula.lint'

            repositories { mavenCentral() }
            dependencies {
                compile 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                        .methodReferences(conf)
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
           import com.google.common.collect.Sets;
           import com.google.common.util.concurrent.ListeningExecutorService;
           import com.google.common.util.concurrent.MoreExecutors;
           import java.util.HashMap;
           import java.util.Set;
           
           public class Main {
               ListeningExecutorService les = MoreExecutors.sameThreadExecutor();
               Set<String> tester = Sets.newSetFromMap(new HashMap<>());
           }
        """)


        when:
        runTasksSuccessfully('compileMethodReferences')
        String methodReferences =  new File(projectDir, 'compileMethodReferences.txt').text

        then:
        !methodReferences.contains("class: Main | methodName: <init> | owner: java/lang/Object | methodDesc: ()V | line: 8")
        methodReferences.contains("class: Main | methodName: sameThreadExecutor | owner: com/google/common/util/concurrent/MoreExecutors | methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; | line: 9 | isInterface: false")
        methodReferences.contains("class: Main | methodName: newSetFromMap | owner: com/google/common/collect/Sets | methodDesc: (Ljava/util/Map;)Ljava/util/Set; | line: 10 | isInterface: false")
    }

    def 'find method references - ignore package'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
            }

            apply plugin: 'nebula.lint'

            repositories { mavenCentral() }
            dependencies {
                compile 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                        .methodReferences(conf, ['com/google/common/collect'])
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
           import com.google.common.collect.Sets;
           import com.google.common.util.concurrent.ListeningExecutorService;
           import com.google.common.util.concurrent.MoreExecutors;
           import java.util.HashMap;
           import java.util.Set;
           
           public class Main {
               ListeningExecutorService les = MoreExecutors.sameThreadExecutor();
               Set<String> tester = Sets.newSetFromMap(new HashMap<>());
           }
        """)


        when:
        runTasksSuccessfully('compileMethodReferences')
        String methodReferences =  new File(projectDir, 'compileMethodReferences.txt').text

        then:
        !methodReferences.contains("class: Main | methodName: <init> | owner: java/lang/Object | methodDesc: ()V | line: 8")
        !methodReferences.contains("class: Main | methodName: newSetFromMap | owner: com/google/common/collect/Sets | methodDesc: (Ljava/util/Map;)Ljava/util/Set; | line: 10")
        methodReferences.contains("class: Main | methodName: sameThreadExecutor | owner: com/google/common/util/concurrent/MoreExecutors | methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; | line: 9 | isInterface: false")
    }

    def 'find method references - multiple classes'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
            }

            apply plugin: 'nebula.lint'

            repositories { mavenCentral() }
            dependencies {
                compile 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                        .methodReferences(conf)
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
           import com.google.common.util.concurrent.ListeningExecutorService;
           import com.google.common.util.concurrent.MoreExecutors;
           
           public class Main {
               ListeningExecutorService les = MoreExecutors.sameThreadExecutor();
           }
        """)

        createJavaSourceFile("""
           import com.google.common.collect.Sets;
           import java.util.HashMap;
           import java.util.Set;
           
           public class Main2 {
               Set<String> tester = Sets.newSetFromMap(new HashMap<>());
           }
        """)


        when:
        runTasksSuccessfully('compileMethodReferences')
        String methodReferences =  new File(projectDir, 'compileMethodReferences.txt').text

        then:
        methodReferences.contains("class: Main | methodName: sameThreadExecutor | owner: com/google/common/util/concurrent/MoreExecutors | methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; | line: 6 | isInterface: false")
        methodReferences.contains("class: Main2 | methodName: newSetFromMap | owner: com/google/common/collect/Sets | methodDesc: (Ljava/util/Map;)Ljava/util/Set; | line: 7 | isInterface: false")
    }


    def createJavaSourceFile(String source) {
        createJavaSourceFile(projectDir, source)
    }

    def createJavaSourceFile(File projectDir, String source) {
        createJavaFile(projectDir, source, 'src/main/java')
    }

    def createJavaFile(File projectDir, String source, String sourceFolderPath) {
        def sourceFolder = new File(projectDir, sourceFolderPath)
        sourceFolder.mkdirs()
        new File(sourceFolder, JavaFixture.fullyQualifiedName(source).replaceAll(/\./, '/') + '.java').text = source
    }
}
