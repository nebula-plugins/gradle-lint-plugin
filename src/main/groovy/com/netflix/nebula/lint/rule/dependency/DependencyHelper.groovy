package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.GradleViolation
import com.netflix.nebula.lint.rule.GradleDependency
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.GStringExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression

class DependencyHelper {
    static void removeVersion(GradleViolation violation, MethodCallExpression call, GradleDependency dep) {
        GradleDependency depClone = dep.clone()
        depClone.version = ''
        if (call.arguments.expressions.size == 1 && call.arguments.expressions[0] instanceof ConstantExpression) {
            handleConstantExpression(violation, call.arguments.expressions[0], "'${depClone.toNotation()}'")
        } else if (call.arguments.expressions.size == 1 && call.arguments.expressions[0] instanceof GStringExpression) {
            handleGStringExpression(violation, call.arguments.expressions[0], "\"${depClone.toNotation()}\"")
        } else if (call.arguments.expressions.size == 1 && call.arguments.expressions[0] instanceof NamedArgumentListExpression) {
            removeNamedArgumentListExpression(violation, call.arguments.expressions[0], 'version')
        } else if (call.arguments.expressions.size == 2 && call.arguments.expressions[1] instanceof ClosureExpression) {
            if (!closureContainsForce(call.arguments.expressions[1])) {
                def depExpression = call.arguments.expressions[0]
                if (depExpression instanceof ConstantExpression) {
                    handleConstantExpression(violation, depExpression, "'${depClone.toNotation()}'")
                } else if (depExpression instanceof GStringExpression) {
                    handleGStringExpression(violation, call.arguments.expressions[0], "\"${depClone.toNotation()}\"")
                } else if (depExpression instanceof MapExpression) {
                    removeMapExpression(violation, call.arguments.expressions[0], 'version')
                }
            }
        }
    }

    static void replaceVersion(GradleViolation violation, MethodCallExpression call, GradleDependency dep, String replacement) {
        GradleDependency depClone = dep.clone()
        depClone.version = replacement
        if (call.arguments.expressions.size == 1 && call.arguments.expressions[0] instanceof ConstantExpression) {
            handleConstantExpression(violation, call.arguments.expressions[0], "'${depClone.toNotation()}'")
        } else if (call.arguments.expressions.size == 1 && call.arguments.expressions[0] instanceof NamedArgumentListExpression) {
            replaceNamedArgumentListExpression(violation, call.arguments.expressions[0], 'version', replacement)
        } else if (call.arguments.expressions.size == 2 && call.arguments.expressions[1] instanceof ClosureExpression) {
            def depExpression = call.arguments.expressions[0]
            if (depExpression instanceof ConstantExpression) {
                handleConstantExpression(violation, depExpression, "'${depClone.toNotation()}'")
            } else if (depExpression instanceof MapExpression) {
                replaceMapExpression(violation, call.arguments.expressions[0], 'version', replacement)
            }
        }
    }

    static void replaceDependency(GradleViolation violation, MethodCallExpression call, GradleDependency dep) {
        if (call.arguments.expressions.size == 1 && call.arguments.expressions[0] instanceof ConstantExpression) {
            handleConstantExpression(violation, call.arguments.expressions[0], "'${dep.toNotation()}'")
        } else if (call.arguments.expressions.size == 1 && call.arguments.expressions[0] instanceof NamedArgumentListExpression) {
            replaceNamedArgumentListExpression(violation, call.arguments.expressions[0], dep.toMap())
        } else if (call.arguments.expressions.size == 2 && call.arguments.expressions[1] instanceof ClosureExpression) {
            def depExpression = call.arguments.expressions[0]
            if (depExpression instanceof ConstantExpression) {
                handleConstantExpression(violation, depExpression, "'${dep.toNotation()}'")
            } else if (depExpression instanceof MapExpression) {
                replaceMapExpression(violation, call.arguments.expressions[0], dep.toMap())
            }
        }
    }

    private static Boolean closureContainsForce(ClosureExpression expr) {
        return expr.code.statements.any { (it.expression instanceof BinaryExpression) &&
                ((BinaryExpression)it.expression).leftExpression?.variable == 'force' &&
                ((BinaryExpression)it.expression).rightExpression?.value == true }
    }

    private static void handleConstantExpression(GradleViolation violation, ConstantExpression expr, String text) {
        violation.replaceWith(expr, text)
    }

    private static void handleGStringExpression(GradleViolation violation, GStringExpression expr, String text) {
        if (expr.verbatimText.matches(/[^:]+:[^:]+:\$[^:]+/)) { // only match <group>:<name>:$<variable>
            violation.replaceWith(expr, text)
        }

    }

    private static void removeNamedArgumentListExpression(GradleViolation violation, NamedArgumentListExpression expr, String key) {
        removeMapLikeExpression(violation, expr, key)
    }

    private static void removeMapExpression(GradleViolation violation, MapExpression expr, String key) {
        removeMapLikeExpression(violation, expr, key)
    }

    private static void removeMapLikeExpression(GradleViolation violation, mapLikeExpr, String key) {
        def mapEntries = mapLikeExpr.mapEntryExpressions.clone()
        def mapString = mapEntries.findAll { it.keyExpression.value != key }
                .collect { "${it.keyExpression.value}: '${it.valueExpression.value}'" }
                .join(', ')
        violation.replaceWith(mapLikeExpr, mapString)
    }

    private static void replaceNamedArgumentListExpression(GradleViolation violation, NamedArgumentListExpression expr, String key, String newValue) {
        replaceMapLikeExpression(violation, expr, key, newValue)
    }

    private static void replaceMapExpression(GradleViolation violation, MapExpression expr, String key, String newValue) {
        replaceMapLikeExpression(violation, expr, key, newValue)
    }

    private static void replaceMapLikeExpression(GradleViolation violation, mapLikeExpr, String key, String newValue) {
        def mapEntries = mapLikeExpr.mapEntryExpressions.clone()
        def mapString = mapEntries
                .collect { (it.keyExpression.value == key && it.valueExpression instanceof ConstantExpression) ? "${it.keyExpression.value}: '${newValue}'" : "${it.keyExpression.value}: '${it.valueExpression.value}'" }
                .join(', ')
        violation.replaceWith(mapLikeExpr, mapString)
    }

    private static void replaceNamedArgumentListExpression(GradleViolation violation, NamedArgumentListExpression expr, Map<String, String> replacements) {
        replaceMapLikeExpression(violation, expr, replacements)
    }

    private static void replaceMapExpression(GradleViolation violation, MapExpression expr, Map<String, String> replacements) {
        replaceMapLikeExpression(violation, expr, replacements)
    }

    private static void replaceMapLikeExpression(GradleViolation violation, mapLikeExpr, Map<String, String> replacements) {
        def mapEntries = mapLikeExpr.mapEntryExpressions.clone()
        def mapString = mapEntries
                .collect { replacements.keySet().contains(it.keyExpression.value) ? "${it.keyExpression.value}: '${replacements.get(it.keyExpression.value)}'" : "${it.keyExpression.value}: '${it.valueExpression.value}'" }
                .join(', ')
        violation.replaceWith(mapLikeExpr, mapString)
    }
}
