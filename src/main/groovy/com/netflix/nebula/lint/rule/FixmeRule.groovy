package com.netflix.nebula.lint.rule

import groovy.transform.CompileStatic
import org.codenarc.rule.Rule
import org.codenarc.rule.Violation
import org.codenarc.source.SourceCode

@CompileStatic
class FixmeRule implements Rule {
    @Override
    List<Violation> applyTo(SourceCode sourceCode) {
        throw new RuntimeException('This should never be called')
    }

    @Override
    int getPriority() {
        // A system property is used since not all lint rules are GradleModelAware in order to pass in a project
        // We would prefer to use https://docs.gradle.org/6.1/javadoc/org/gradle/api/provider/ProviderFactory.html#systemProperty-java.lang.String-
        // but this was introduced in Gradle 6.1
        def nonCriticalPriority = System.getProperty('nebula.lint.fixmeAsNonCritical')
        if (nonCriticalPriority != null && nonCriticalPriority == "true") {
            return 2 // violations of fixmes are noncritical only when the property is used
        }
        return 1 // violations of fixmes are always critical rule failures
    }

    @Override
    String getName() {
        return 'expired-fixme'
    }

    @Override
    int getCompilerPhase() {
        return 0 // irrelevant
    }
}
