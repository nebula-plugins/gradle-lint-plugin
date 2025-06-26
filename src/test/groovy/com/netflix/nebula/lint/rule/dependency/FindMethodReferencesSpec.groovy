package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.BaseIntegrationTestKitSpec
import spock.lang.Subject

@Subject(DependencyService)
class FindMethodReferencesSpec extends BaseIntegrationTestKitSpec {

    def setup(){
        disableConfigurationCache()
    }

    def 'find method references no exclusion'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }

            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        org.gradle.internal.deprecation.DeprecationLogger.whileDisabled {
                            new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                            .methodReferences(conf)
                            .join('\\n')
                        }
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
        runTasks('compileClasspathMethodReferences')
        String methodReferences = new File(projectDir, 'compileClasspathMethodReferences.txt').text

        then:
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: <init> - owner: java/lang/Object - methodDesc: ()V - line: 10 - isInterface: false - opCode: INVOKESPECIAL | methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 11 - isInterface: false - opCode: INVOKESTATIC | methodName: <init> - owner: java/util/HashMap - methodDesc: ()V - line: 12 - isInterface: false - opCode: INVOKESPECIAL | methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 12 - isInterface: false - opCode: INVOKESTATIC"
    }


    def 'find method references excluding'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
            
            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                       org.gradle.internal.deprecation.DeprecationLogger.whileDisabled {
                            new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                            .methodReferencesExcluding(conf)
                            .join('\\n')
                        }
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
        runTasks('compileClasspathMethodReferences')
        String methodReferences = new File(projectDir, 'compileClasspathMethodReferences.txt').text

        then:
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 11 - isInterface: false - opCode: INVOKESTATIC | methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 12 - isInterface: false - opCode: INVOKESTATIC"
    }

    def 'find method references - include only'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
            
            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                       org.gradle.internal.deprecation.DeprecationLogger.whileDisabled {
                            new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                            .methodReferencesIncludeOnly(conf, ['com/google/common/collect'])
                            .join('\\n')
                       }
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
        def r = runTasks('compileClasspathMethodReferences')
        String methodReferences = new File(projectDir, 'compileClasspathMethodReferences.txt').text

        then:
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 11 - isInterface: false - opCode: INVOKESTATIC"
    }


    def 'find method references - exclude specific package'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
            
            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        org.gradle.internal.deprecation.DeprecationLogger.whileDisabled {
                            new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                            .methodReferencesExcluding(conf, ['com/google/common/collect'])
                            .join('\\n')
                        }
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
        runTasks('compileClasspathMethodReferences')
        String methodReferences = new File(projectDir, 'compileClasspathMethodReferences.txt').text

        then:
        methodReferences == "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 11 - isInterface: false - opCode: INVOKESTATIC"
    }

    def 'find method references - multiple classes'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
           

            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        org.gradle.internal.deprecation.DeprecationLogger.whileDisabled {
    
                            new File(projectDir, "\${conf}MethodReferences.txt").text = DependencyService.forProject(project)
                            .methodReferencesExcluding(conf)
                            .join('\\n')
                        }
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
        runTasks('compileClasspathMethodReferences')
        String methodReferences = new File(projectDir, 'compileClasspathMethodReferences.txt').text

        then:
        methodReferences.contains "source: Main2.java - filePath: com/netflix/test/Main2.java - name: com/netflix/test/Main2 - methodReferences: methodName: newSetFromMap - owner: com/google/common/collect/Sets - methodDesc: (Ljava/util/Map;)Ljava/util/Set; - line: 9 - isInterface: false - opCode: INVOKESTATIC"
        methodReferences.contains "source: Main.java - filePath: com/netflix/test/Main.java - name: com/netflix/test/Main - methodReferences: methodName: sameThreadExecutor - owner: com/google/common/util/concurrent/MoreExecutors - methodDesc: ()Lcom/google/common/util/concurrent/ListeningExecutorService; - line: 8 - isInterface: false - opCode: INVOKESTATIC"
    }

    def 'gets dependency references'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
           

            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:19.0'
            }

            import groovy.json.*

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        org.gradle.internal.deprecation.DeprecationLogger.whileDisabled {
                            println new JsonBuilder( DependencyService.forProject(project).methodReferences(conf) ).toPrettyString() 
                        }
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
        def result = runTasks('compileClasspathMethodReferences')

        then:
        result.output.contains('"methodName": "sameThreadExecutor"')
        result.output.contains('"methodName": "newSetFromMap"')
        result.output.contains('"methodName": "<init>"')
        result.output.contains('"owner": "com/google/common/util/concurrent/MoreExecutors"')
        result.output.contains('"owner": "com/google/common/collect/Sets"')
        result.output.contains('"name": "guava"')
        result.output.contains('"version": "19.0"')
            }

    def 'gets dependency references - multiple dependencies'() {
        buildFile.text = """\
            import com.netflix.nebula.lint.rule.dependency.*

            plugins {
                id 'java'
                id 'com.netflix.nebula.lint'
            }
            
            repositories { mavenCentral() }
            dependencies {
                implementation 'com.google.guava:guava:19.0'
                implementation group: 'org.apache.commons', name: 'commons-lang3', version: '3.8.1'
            }

            import groovy.json.*

             // a task to generate an unused dependency report for each configuration
            project.configurations.collect { it.name }.each { conf ->
                task "\${conf}MethodReferences"(dependsOn: compileTestJava) {
                    doLast {
                        org.gradle.internal.deprecation.DeprecationLogger.whileDisabled {
                            println new JsonBuilder( DependencyService.forProject(project).methodReferencesExcluding(conf) ).toPrettyString() 
                        }
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
        def result = runTasks('compileClasspathMethodReferences')

        then:
        result.output.contains('"methodName": "sameThreadExecutor"')
        result.output.contains('"methodName": "newSetFromMap"') 
        result.output.contains('"methodName": "deleteWhitespace"')
        result.output.contains('"owner": "com/google/common/util/concurrent/MoreExecutors"')
        result.output.contains('"owner": "com/google/common/collect/Sets"')
        result.output.contains('"owner": "org/apache/commons/lang3/StringUtils"')
        result.output.contains('"name": "guava"')
        result.output.contains('"version": "19.0"')
        result.output.contains('"name": "commons-lang3"')
        result.output.contains('"version": "3.8.1"')
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
