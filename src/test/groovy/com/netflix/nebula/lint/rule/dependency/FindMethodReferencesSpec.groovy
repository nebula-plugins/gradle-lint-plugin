package com.netflix.nebula.lint.rule.dependency

import nebula.test.IntegrationSpec

class FindMethodReferencesSpec extends IntegrationSpec {

    def 'find method references no exclusion'() {
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
           package com.netflix.test;

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
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: <init> - owner: java/lang/Object - methodDesc: ()V - line: 10 - isInterface: false - opCode: INVOKESPECIAL | methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 11 - isInterface: false - opCode: INVOKESTATIC | methodName: <init> - owner: java/util/HashMap - methodDesc: ()V - line: 12 - isInterface: false - opCode: INVOKESPECIAL | methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 12 - isInterface: false - opCode: INVOKESTATIC"
    }


    def 'find method references excluding'() {
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
                        .methodReferencesExcluding(conf)
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
            package com.netflix.test;

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
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 11 - isInterface: false - opCode: INVOKESTATIC | methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 12 - isInterface: false - opCode: INVOKESTATIC"
    }

    def 'find method references - include only'() {
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
                        .methodReferencesIncludeOnly(conf, ['com/google/common/collect'])
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
           package com.netflix.test;
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
        def r = runTasksSuccessfully('compileMethodReferences')
        String methodReferences =  new File(projectDir, 'compileMethodReferences.txt').text

        then:
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 11 - isInterface: false - opCode: INVOKESTATIC"
    }


    def 'find method references - exclude specific package'() {
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
                        .methodReferencesExcluding(conf, ['com/google/common/collect'])
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
           package com.netflix.test;

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
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 11 - isInterface: false - opCode: INVOKESTATIC"
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
                        .methodReferencesExcluding(conf)
                        .join('\\n')
                    }
                  
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
           package com.netflix.test;

           import com.google.common.util.concurrent.ListeningExecutorService;
           import com.google.common.util.concurrent.MoreExecutors;
           
           public class Main {
               ListeningExecutorService les = MoreExecutors.sameThreadExecutor();
           }
        """)

        createJavaSourceFile("""
           package com.netflix.test;
           
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
        methodReferences.contains "source: Main2.java - filePath: com/netflix/test/Main2.java - name: com/netflix/test/Main2 - methodReferences: methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 9 - isInterface: false - opCode: INVOKESTATIC"
        methodReferences.contains "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 8 - isInterface: false - opCode: INVOKESTATIC"
    }

    def 'gets dependency references'() {
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

            import groovy.json.*

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        println new JsonBuilder( DependencyService.forProject(project).methodReferences(conf) ).toPrettyString() 
                    }
                }
            }
            """.stripMargin()

        createJavaSourceFile("""
           package com.netflix.test;

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
        def result = runTasksSuccessfully('compileMethodReferences')

        then:
        result.standardOutput.contains("""[
    {
        "methodReferences": [
            {
                "opCode": "INVOKESPECIAL",
                "isInterface": false,
                "owner": "java/lang/Object",
                "methodDesc": "()V",
                "line": 10,
                "methodName": "<init>",
                "artifacts": [
                    
                ]
            },
            {
                "opCode": "INVOKESTATIC",
                "isInterface": false,
                "owner": "com/google/common/util/concurrent/MoreExecutors",
                "methodDesc": "()Lcom/google/common/util/concurrent/ListeningExecutorService;",
                "line": 11,
                "methodName": "sameThreadExecutor",
                "artifacts": [
                    {
                        "type": "jar",
                        "version": "19.0",
                        "name": "guava",
                        "organization": "com.google.guava"
                    }
                ]
            },
            {
                "opCode": "INVOKESPECIAL",
                "isInterface": false,
                "owner": "java/util/HashMap",
                "methodDesc": "()V",
                "line": 12,
                "methodName": "<init>",
                "artifacts": [
                    
                ]
            },
            {
                "opCode": "INVOKESTATIC",
                "isInterface": false,
                "owner": "com/google/common/collect/Sets",
                "methodDesc": "(Ljava/util/Map;)Ljava/util/Set;",
                "line": 12,
                "methodName": "newSetFromMap",
                "artifacts": [
                    {
                        "type": "jar",
                        "version": "19.0",
                        "name": "guava",
                        "organization": "com.google.guava"
                    }
                ]
            }
        ],
        "filePath": "com/netflix/test/Main.java",
        "source": "Main.java",
        "name": "com/netflix/test/Main"
    }
]
""")
            }

    def 'gets dependency references - multiple dependencies'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
            }

            apply plugin: 'nebula.lint'

            repositories { mavenCentral() }
            dependencies {
                compile 'com.google.guava:guava:19.0'
                compile group: 'org.apache.commons', name: 'commons-lang3', version: '3.8.1'
            }

            import groovy.json.*

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        println new JsonBuilder( DependencyService.forProject(project).methodReferencesExcluding(conf) ).toPrettyString() 
                    }
                }                     
            }
            """.stripMargin()

        createJavaSourceFile("""
           package com.netflix.test;

           import org.apache.commons.lang3.StringUtils;
           import com.google.common.collect.Sets;
           import com.google.common.util.concurrent.ListeningExecutorService;
           import com.google.common.util.concurrent.MoreExecutors;
           import java.util.*; 
           
           public class Main {
               ListeningExecutorService les = MoreExecutors.sameThreadExecutor();
               Set<String> tester = Sets.newSetFromMap(new HashMap<>());
               public void test() {
                    System.out.println(StringUtils.deleteWhitespace("   ab  c  "));    
               }      
           }
        """)


        when:
        def result = runTasks('compileMethodReferences')

        then:
        result.standardOutput.contains("""[
    {
        "methodReferences": [
            {
                "opCode": "INVOKESTATIC",
                "isInterface": false,
                "owner": "com/google/common/util/concurrent/MoreExecutors",
                "methodDesc": "()Lcom/google/common/util/concurrent/ListeningExecutorService;",
                "line": 11,
                "methodName": "sameThreadExecutor",
                "artifacts": [
                    {
                        "type": "jar",
                        "version": "19.0",
                        "name": "guava",
                        "organization": "com.google.guava"
                    }
                ]
            },
            {
                "opCode": "INVOKESTATIC",
                "isInterface": false,
                "owner": "com/google/common/collect/Sets",
                "methodDesc": "(Ljava/util/Map;)Ljava/util/Set;",
                "line": 12,
                "methodName": "newSetFromMap",
                "artifacts": [
                    {
                        "type": "jar",
                        "version": "19.0",
                        "name": "guava",
                        "organization": "com.google.guava"
                    }
                ]
            },
            {
                "opCode": "INVOKESTATIC",
                "isInterface": false,
                "owner": "org/apache/commons/lang3/StringUtils",
                "methodDesc": "(Ljava/lang/String;)Ljava/lang/String;",
                "line": 14,
                "methodName": "deleteWhitespace",
                "artifacts": [
                    {
                        "type": "jar",
                        "version": "3.8.1",
                        "name": "commons-lang3",
                        "organization": "org.apache.commons"
                    }
                ]
            }
        ],
        "filePath": "com/netflix/test/Main.java",
        "source": "Main.java",
        "name": "com/netflix/test/Main"
    }
]
""")
    }



    def createJavaSourceFile(String source) {
        createJavaSourceFile(projectDir, source)
    }

    def createJavaSourceFile(File projectDir, String source) {
        createJavaFile(projectDir, source, 'src/main/java/com/netflix/test')
    }

    def createJavaFile(File projectDir, String source, String sourceFolderPath) {
        def sourceFolder = new File(projectDir, sourceFolderPath)
        sourceFolder.mkdirs()
        new File(projectDir.toString() + '/src/main/java', JavaFixture.fullyQualifiedName(source).replaceAll(/\./, '/') + '.java').text = source
    }
}
