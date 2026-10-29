package com.netflix.nebula.lint.rule.dsl

import com.netflix.nebula.lint.rule.ModelAwareGradleLintRule
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement

class SpaceAssignmentRule extends ModelAwareGradleLintRule {

    String description = 'space-assignment syntax is deprecated'

    private final Set<Expression> statementExpressions = Collections.newSetFromMap(new IdentityHashMap<Expression, Boolean>())

    /**
     * Space assignment is a whole statement, such as {@code url "https://example.com"}.
     * The same call used as a value, such as inside a string or as an operand, is an ordinary method call.
     */
    @Override
    void visitExpressionStatement(ExpressionStatement statement) {
        statementExpressions.add(statement.expression)
    }

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        if (!statementExpressions.contains(call) || dslStack().contains('plugins')) {
            return
        }
        if (call.methodAsString == 'group' && !isGradleGroup(call)) {
            return
        }
        if (call.arguments.size() != 1 || call.arguments[-1] instanceof ClosureExpression) {
            return
        }

        def receiverClass = receiver(call)?.clazz
        if (receiverClass == null) {
            return // no enough data to analyze
        }

        def invokedMethodName = call.method.value

        // check if the method has a matching property
        def setter = receiverClass.getMethods().find { it.name == "set${invokedMethodName.capitalize()}" }
        if (setter == null) {
            return // no matching property
        }

        // check if it's a generated method for space assignment
        def exactMethod = receiverClass.getMethods().find { it.name == invokedMethodName }
        if (exactMethod != null) {
            def deprecatedAnnotation = exactMethod.getAnnotation(Deprecated)
            if (deprecatedAnnotation != null && !isDeprecatedVarargsMethod(exactMethod)) {
                // may be false positive when the explicit method is deprecated
                addViolation(call)
            }
        } else {
            addViolation(call)
        }
    }

    /**
     * Gradle's generated space-assignment shims take a single argument, so a deprecated varargs method is a real
     * API that was deprecated, not a shim. Rewriting it to an assignment produces a build that fails with
     * "Could not set unknown property".
     * <p>
     * The one case known today is {@code MavenArtifactRepository.artifactUrls(Object...)}, deprecated in Gradle 9.6.
     * It has no replacement property, so a space-assignment fix is never valid for it. Other rules may offer a
     * different migration for it, and this skip keeps this rule from conflicting with their fixes.
     * It is not a general opt-out for deprecated methods.
     */
    private static boolean isDeprecatedVarargsMethod(java.lang.reflect.Method method) {
        return method.isVarArgs()
    }

    private boolean isGradleGroup(MethodCallExpression call) {
        if (call.methodAsString != 'group') {
            return false
        }

        return dslStack().empty ||
                dslStack().containsAll(['subprojects']) ||
                dslStack().containsAll(['allprojects']) ||
                dslStack().contains('configureEach')
    }

    private void addViolation(MethodCallExpression call) {
        addBuildLintViolation(description, call)
                .replaceWith(call.method, "${call.methodAsString} =")
    }

}
