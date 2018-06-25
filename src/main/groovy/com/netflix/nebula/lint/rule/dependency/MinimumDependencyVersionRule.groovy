package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.Incubating
import org.gradle.api.artifacts.Configuration
import org.gradle.util.VersionNumber

/**
 * This is like a declarative form of the use of a Substitute Nebula Resolution Rule:
 * https://github.com/nebula-plugins/gradle-resolution-rules-plugin/wiki/Dependency-Rule-Types#minimum-version-rule
 *
 * Continuing confluence of these two plugins may eventually result in a different way to parameterize the rule.
 */
@Incubating
class MinimumDependencyVersionRule extends GradleLintRule implements GradleModelAware {
    String description = 'pull up dependencies to a minimum version if necessary'
    def alreadyAdded = [] as Set
    def resolvableAndResolvedConfigurations

    @Lazy
    List<GradleDependency> minimumVersions = {
        if (project.hasProperty('gradleLint.minVersions')) {
            project.property('gradleLint.minVersions')?.
                    toString()?.
                    split(',')?.
                    collect { GradleDependency.fromConstant(it) }?.
                    findAll { it != null } ?:
                    []
        } else []
    }()

    @Override
    protected void beforeApplyTo() {
        def dependencyService = DependencyService.forProject(project)
        resolvableAndResolvedConfigurations = dependencyService.resolvableAndResolvedConfigurations()
    }

    @Override
    void visitGradleResolutionStrategyForce(MethodCallExpression call, String conf, Map<GradleDependency, Expression> forces) {
        forces.each { dep, force ->
            fixDependencyDeclarationIfNecessary(force, conf, dep)
        }
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        fixDependencyDeclarationIfNecessary(call.arguments, conf, dep)
    }

    private void fixDependencyDeclarationIfNecessary(ASTNode decl, String conf, GradleDependency dep) {
        def minVersionConstraint = minimumVersions.find { it.group == dep.group && it.name == dep.name }
        if (!minVersionConstraint) {
            return // nothing to do
        }

        if (!resolvableAndResolvedConfigurations.collect { it.name }.contains(conf)) {
            return // we won't slow down the build by resolving the configuration if it hasn't been already
        }

        if (!dep.version) {
            return // we assume that anything recommending this version is behaving correctly
        }

        // causes the component metadata rule to fire and capture all metadata by module id
        def resolved = project.configurations.getByName(conf).resolvedConfiguration.resolvedArtifacts.find {
            it.moduleVersion.id.group == dep.group && it.moduleVersion.id.name == dep.name
        }

        if (!resolved)
            return

        if (VersionNumber.parse(resolved.moduleVersion.id.version).compareTo(VersionNumber.parse(minVersionConstraint.version)) < 0) {
            addBuildLintViolation("this dependency does not meet the minimum version of $minVersionConstraint.version", decl)
                    .replaceWith(decl, "'${minVersionConstraint.toNotation()}'")
            alreadyAdded += minVersionConstraint
        }
    }

    @Override
    void visitDependencies(MethodCallExpression call) {
        bookmark('dependencies', call)
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        project.configurations.each { conf ->
            if (resolvableAndResolvedConfigurations.contains(conf)) {
                minimumVersions.each { constraint ->
                    if (!alreadyAdded.contains(constraint) && addFirstOrderIfNecessary(conf, constraint))
                        alreadyAdded += constraint
                }
            }
        }
    }

    private boolean addFirstOrderIfNecessary(Configuration conf, GradleDependency constraint) {
        if (!resolvableAndResolvedConfigurations.contains(conf))
            return false

        def resolved = conf.resolvedConfiguration.resolvedArtifacts*.moduleVersion*.id.find {
            constraint.group == it.group && constraint.name == it.name
        }

        if (!resolved)
            return false

        // add the first order dependency to the lowest configuration violating the constraint and none of its extending configurations
        if (conf.extendsFrom.any { addFirstOrderIfNecessary(it, constraint) })
            return true

        if (constraint && VersionNumber.parse(resolved.version).compareTo(VersionNumber.parse(constraint.version)) < 0) {
            def dependenciesBlock = bookmark('dependencies')
            if (dependenciesBlock) {
                addBuildLintViolation("$constraint.group:$constraint.name is below the minimum version of $constraint.version")
                        .insertIntoClosure(dependenciesBlock, "$conf.name '${constraint.toNotation()}'")
            } else {
                addBuildLintViolation("$constraint.group:$constraint.name is below the minimum version of $constraint.version")
                // FIXME insert new dependencies block with this dependency
            }
            return true
        }

        return false
    }
}
