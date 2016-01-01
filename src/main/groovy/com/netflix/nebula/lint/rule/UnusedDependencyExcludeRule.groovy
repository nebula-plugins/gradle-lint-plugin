package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.rule.AbstractAstVisitorRule
import org.codenarc.rule.AstVisitor
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.specs.Specs

class UnusedDependencyExcludeRule extends AbstractAstVisitorRule implements GradleModelAware {
    String name = "unused-dependency-exclude"
    int priority = 2
    Project project

    @Override
    AstVisitor getAstVisitor() {
        return new UnusedDependencyExcludeVisitor(project: project)
    }
}

class UnusedDependencyExcludeVisitor extends AbstractGradleLintVisitor {
    GradleDependency dependency

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        dependency = dep
        super.visitMethodCallExpression(call)
        dependency = null
    }

    @Override
    protected void visitMethodCallExpressionInternal(MethodCallExpression call) {
        if(dependency) {
            // https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/ModuleDependency.html#exclude(java.util.Map)
            if(call.methodAsString == 'exclude') {
                def entries = collectEntryExpressions(call)
                if(isExcludeUnnecessary(entries.group, entries.module))
                    addViolationToDelete(call, "the excluded dependency is not a transitive of $dependency.group:$dependency.name:$dependency.version, so has no effect")
            }
        }
        super.visitMethodCallExpressionInternal(call)
    }

    private boolean isExcludeUnnecessary(String group, String name) {
        // Since Gradle does not expose any information about which excludes were effective, we will create a new configuration
        // lintExcludeConf, add the dependency and resolve it.
        Configuration lintExcludeConf = project.configurations.create("lintExcludes")
        project.dependencies.add(lintExcludeConf.name, "$dependency.group:$dependency.name:$dependency.version")

        // If we find a dependency in the transitive closure of this special conf, then we can infer that the exclusion is
        // doing something. Note that all*.exclude style exclusions are applied to all of the configurations at the time
        // of project evaluation, but not lintExcludeConf.
        def excludeIsInTransitiveClosure = false
        def deps = lintExcludeConf.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)
        while(!deps.isEmpty() && !excludeIsInTransitiveClosure) {
            deps = deps.collect { d ->
                if((!group || d.moduleGroup == group) && (!name || d.moduleName == name)) {
                    excludeIsInTransitiveClosure = true
                }
                d.children
            }
            .flatten()
        }

        project.configurations.remove(lintExcludeConf)

        !excludeIsInTransitiveClosure
    }
}
