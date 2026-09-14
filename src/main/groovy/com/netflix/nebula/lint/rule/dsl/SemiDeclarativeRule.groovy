package com.netflix.nebula.lint.rule.dsl

import com.netflix.nebula.lint.rule.ModelAwareGradleLintRule
import org.codehaus.groovy.ast.ClassNode

/**
 * This rule will detect non-declarative code within build scripts.
 * Buildscripts should be as declarative as possible:
 * Classes needed for buildscripts should be declared in buildSrc or a plugin
 * "Semi declarative" means we want to be as declarative as possible, even when not using the actual "declarative gradle" feature of Gradle.
 */
class SemiDeclarativeRule extends ModelAwareGradleLintRule {
    @Override
    String getDescription() {
        return null
    }

    @Override
    void visitClass(ClassNode classNode) {
        if (classNode.name != "None") {
            addBuildLintViolation("Don't declare classes in buildscripts. " +
                    "Try to keep buildscripts declarative. " +
                    "Declare classes / tasks in buildSrc or a plugin.",
                    classNode)
        }
    }
}
