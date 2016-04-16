package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.Configuration
import org.gradle.api.specs.Specs

class UnusedExcludeByConfigurationRule extends GradleLintRule implements GradleModelAware {
    @Override
    void visitConfigurationExclude(MethodCallExpression call, String conf, GradleDependency exclude) {
        // Since Gradle does not expose any information about which excludes were effective, we will create a new configuration
        // lintExcludeConf, adding all first order dependencies from the conf that the exclusion applies to and resolve it.
        Configuration lintExcludeConf = project.configurations.create("lintExcludes")
        project.configurations.findAll { (conf == 'all' || conf == it.name) && it != lintExcludeConf }*.allDependencies.flatten().each { d ->
            project.dependencies.add(lintExcludeConf.name, "$d.group:$d.name:$d.version")
        }

        // If we find a dependency in the transitive closure of this special conf, then we can infer that the exclusion is
        // doing something. Note that all*.exclude style exclusions are applied to all of the configurations at the time
        // of project evaluation, but not lintExcludeConf.
        def excludeIsInTransitiveClosure = false
        def deps = lintExcludeConf.resolvedConfiguration.lenientConfiguration.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)
        while(!deps.isEmpty() && !excludeIsInTransitiveClosure) {
            deps = deps.collect { d ->
                if((!exclude.group || d.moduleGroup == exclude.group) && (!exclude.name || d.moduleName == exclude.name)) {
                    excludeIsInTransitiveClosure = true
                }
                d.children
            }
            .flatten()
        }

        project.configurations.remove(lintExcludeConf)

        if(!excludeIsInTransitiveClosure) {
            addLintViolation('the exclude dependency is not in your dependency graph, so has no effect', call)
                .delete(call)
        }
    }
}
