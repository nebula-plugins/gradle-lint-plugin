package com.netflix.nebula.lint.issues

import com.netflix.nebula.lint.plugin.NotNecessarilyGitRepository
import org.eclipse.jgit.api.ApplyCommand
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class Issue37Spec extends Specification {
    @Rule
    TemporaryFolder temp

    def 'patch fails to apply'() {
        setup:
        def build = temp.newFile('build.gradle')
        def patch = temp.newFile('lint.patch')

        build << '''\
dependencies {
    compile('log4j:log4j:1.2.17')
    compile('com.google.guava:guava:19.0')
    compile('org.apache.commons:commons-lang3:3.4')

    provided('com.ibm:server-runtime:8.5.0')
    provided 'com.jscape:sftp:9.0.0'
'''

        when:
        patch << '''\
diff --git a/build.gradle b/build.gradle
--- a/build.gradle
+++ b/build.gradle
@@ -1,7 +1,7 @@
 dependencies {
-    compile('log4j:log4j:1.2.17')
+    compile 'log4j:log4j:1.2.17'
-    compile('com.google.guava:guava:19.0')
+    compile 'com.google.guava:guava:19.0'
-    compile('org.apache.commons:commons-lang3:3.4')
+    compile 'org.apache.commons:commons-lang3:3.4'
 
     provided('com.ibm:server-runtime:8.5.0')
     provided 'com.jscape:sftp:9.0.0'
'''

        then:
        new ApplyCommand(new NotNecessarilyGitRepository(temp.root)).setPatch(patch.newInputStream()).call()
    }
}
