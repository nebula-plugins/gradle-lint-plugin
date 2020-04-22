package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BypassedForcesRule extends GradleLintRule implements GradleModelAware {
    String description = 'remove bypassed forces and strict constraints. Works for static and ranged declarations'
    Map<String, Collection<ForcedDependency>> forcedDependenciesPerProject = new HashMap<String, Collection<ForcedDependency>>()
    private final DefaultVersionComparator VERSIONED_COMPARATOR = new DefaultVersionComparator()
    private final DefaultVersionSelectorScheme VERSION_SCHEME = new DefaultVersionSelectorScheme(VERSIONED_COMPARATOR)
    private final Logger log = LoggerFactory.getLogger(BypassedForcesRule.class);

    @Override
    protected void beforeApplyTo() {
        project.rootProject.allprojects { proj ->
            forcedDependenciesPerProject.put(proj.name, new HashSet<ForcedDependency>())
        }
    }

    @Override
    void visitGradleResolutionStrategyForce(MethodCallExpression call, String conf, Map<GradleDependency, Expression> forces) {
        forces.each { dep, force ->
            def top = dslStack().isEmpty() ? "" : dslStack().first()
            def affectedProjects = determineAffectedProjects(call, top)

            affectedProjects.each { affectedProject ->
                forcedDependenciesPerProject.get(affectedProject.name).add(new ForcedDependency(dep, force, conf, 'dependency force'))
            }
        }
    }

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if (!call.arguments.metaClass.getMetaMethod('getExpressions')) {
            return // short-circuit if there are no expressions
        }

        def top = dslStack().isEmpty() ? "" : dslStack().first()
        def affectedProjects = determineAffectedProjects(call, top)

        affectedProjects.each { affectedProject ->
            handleForceInAClosure(call, conf, dep, affectedProject)
            handleVersionConstraintWithStrictVersion(call, conf, dep, affectedProject)
        }
    }

    private void handleForceInAClosure(MethodCallExpression call, String conf, GradleDependency dep, Project affectedProject) {
        if (call.arguments.expressions
                .findAll { it instanceof ClosureExpression }
                .any { closureContainsForce(it as ClosureExpression) }) {
            forcedDependenciesPerProject.get(affectedProject.name).add(new ForcedDependency(dep, call, conf, 'dependency force'))
        }
    }

    private void handleVersionConstraintWithStrictVersion(MethodCallExpression call, String conf, GradleDependency dep, Project affectedProject) {
        List<Map<String, List<String>>> versionConstraintsForAllExpressions = call.arguments.expressions
                .findAll { it instanceof ClosureExpression }
                .collect { gatherVersionConstraints(it as ClosureExpression) }
        if (versionConstraintsForAllExpressions.size() > 0) {
            versionConstraintsForAllExpressions.each { versionConstraints ->
                if (versionConstraints.containsKey('strictly')) {
                    def strictlyValue = versionConstraints.get('strictly')
                    def forcedDependency = new ForcedDependency(dep, call, conf, 'strict version constraint')
                    forcedDependency.setStrictVersion(strictlyValue.first())
                    forcedDependenciesPerProject.get(affectedProject.name).add(forcedDependency)
                }
            }
        }
    }

    @CompileStatic
    @Override
    protected void visitClassComplete(ClassNode node) {
        Collection<BypassedForce> bypassedForces = new ArrayList<BypassedForce>()

        forcedDependenciesPerProject.each { affectedProjectName, forcedDependencies ->
            Project affectedProject = project.rootProject.allprojects.find { it.name == affectedProjectName }
            if (forcedDependencies.size() > 0) {
                findAllBypassedForcesFor(forcedDependencies, affectedProject).each { forcedDep ->
                    bypassedForces.add(new BypassedForce(forcedDep.dep, forcedDep, affectedProjectName))
                }
            }
        }

        bypassedForces
                .groupBy { it.dep }
                .each { dep, bypassedForcesByDep ->
                    Collection<String> projectNames = bypassedForcesByDep.collect { it.projectName }
                    BypassedForce exemplarBypassedForce = bypassedForcesByDep.first()
                    def updatedMessage = exemplarBypassedForce.forcedDependency.message + " for the affected project(s): ${projectNames.sort().join(', ')}"
                    addBuildLintViolation(updatedMessage, exemplarBypassedForce.forcedDependency.forceExpression)
                }
    }

    private Collection<ForcedDependency> findAllBypassedForcesFor(Collection<ForcedDependency> forcedDependencies, Project affectedProject) {
        Collection<ForcedDependency> uniqueBypassedForcedDependencies = new ArrayList<ForcedDependency>()

        if (affectedProject.configurations.size() == 0) {
            return uniqueBypassedForcedDependencies // short-circuit on any project with 0 configurations
        }
        DependencyService dependencyService = DependencyService.forProject(affectedProject)

        def groupedForcedDependencies = forcedDependencies
                .groupBy { it.declaredConfigurationName }
        def resolvableAndResolvedConfigurations = dependencyService.resolvableAndResolvedConfigurations()
        Collection<ForcedDependency> bypassedForcedDependencies = new ArrayList<ForcedDependency>()

        groupedForcedDependencies.each { declaredConfigurationName, forcedDeps ->
            if (declaredConfigurationName == 'all') {
                resolvableAndResolvedConfigurations.each { configuration ->
                    bypassedForcedDependencies.addAll(collectDependenciesWithUnusedForces(configuration, forcedDeps))
                }
            } else {
                Configuration groupedResolvableConfiguration = dependencyService.findResolvableConfiguration(declaredConfigurationName)
                if (resolvableAndResolvedConfigurations.contains(groupedResolvableConfiguration)) {
                    bypassedForcedDependencies.addAll(collectDependenciesWithUnusedForces(groupedResolvableConfiguration, forcedDeps))
                }
            }
        }

        bypassedForcedDependencies.groupBy { it.dep }.each { _dep, forcedDependenciesByDep ->
            forcedDependenciesByDep.groupBy { it.forceExpression }.each { _forceExpression, forcedDependenciesByDepAndForceExpression ->
                if (forcedDependenciesByDepAndForceExpression.size() > 0) {
                    ForcedDependency exemplarForcedDep = forcedDependenciesByDepAndForceExpression.first()
                    uniqueBypassedForcedDependencies.add(exemplarForcedDep)
                }
            }
        }
        return uniqueBypassedForcedDependencies
    }

    @CompileStatic
    private Collection<ForcedDependency> collectDependenciesWithUnusedForces(Configuration configuration, Collection<ForcedDependency> forcedDeps) {
        Collection<ForcedDependency> dependenciesWithUnusedForces = new ArrayList<ForcedDependency>()

        // inspect direct and transitive dependencies
        configuration.resolvedConfiguration.resolvedArtifacts
                .collect { it.moduleVersion.id }
                .unique { it.module.toString() }
                .each { resolvedDep ->
                    forcedDeps.each { forcedDependency ->
                        if (forcedDependency.dep.group == resolvedDep.module.group && forcedDependency.dep.name == resolvedDep.name) {
                            def expectedVersion = ''
                            if (forcedDependency.dep.version != null) {
                                expectedVersion = forcedDependency.dep.version
                            }
                            if (!forcedDependency.strictVersion.isEmpty()) {
                                expectedVersion = forcedDependency.strictVersion
                            }

                            VersionSelector versionSelector = VERSION_SCHEME.parseSelector(expectedVersion)
                            if (!expectedVersion.startsWith('$')
                                    && !expectedVersion.toString().contains(".+")
                                    && !expectedVersion.toString().contains("latest")
                                    && !versionSelector.accept(resolvedDep.version)) {

                                forcedDependency.resolvedConfigurations.add(configuration)
                                forcedDependency.message = "The ${forcedDependency.forceType} has been bypassed.\nRemove or update this value"
                                dependenciesWithUnusedForces.add(forcedDependency)
                            }
                        }
                    }
                }

        return dependenciesWithUnusedForces
    }

    private static Boolean closureContainsForce(ClosureExpression expr) {
        return expr.code.statements.any {
            (it.expression instanceof BinaryExpression) &&
                    ((BinaryExpression) it.expression).leftExpression?.variable == 'force' &&
                    ((BinaryExpression) it.expression).rightExpression?.value == true
        }
    }

    private static Map<String, List<String>> gatherVersionConstraints(ClosureExpression expr) {
        def results = new HashMap<String, List<String>>()
        expr.code.statements.findAll { st ->
            (st.expression instanceof MethodCallExpression) &&
                    ((MethodCallExpression) st.expression)?.methodAsString == 'version' &&
                    ((MethodCallExpression) st.expression)?.arguments?.findAll { arg ->
                        arg instanceof ClosureExpression &&
                                arg?.code instanceof BlockStatement &&
                                arg?.code?.statements?.findAll { stmt ->
                                    stmt?.expression instanceof MethodCallExpression &&
                                            stmt?.expression?.method instanceof ConstantExpression &&
                                            stmt?.expression?.arguments?.expressions?.findAll { expre ->
                                                expre instanceof ConstantExpression &&
                                                        !expre?.value?.equals(null)
                                            }
                                                    ?.each {
                                                        String method = stmt?.expression?.methodAsString
                                                        List values = stmt?.expression?.arguments?.expressions?.collect { it.value as String }

                                                        results.put(method, values)
                                                    }
                                }
                    }
        }
        return results
    }

    private Collection<Project> determineAffectedProjects(MethodCallExpression call, String top) {
        if (top == 'allprojects') {
            return project.rootProject.allprojects
        } else if (top == 'subprojects') {
            return project.rootProject.subprojects
        } else if (top == 'buildscript') {
            // do not pay attention to buildscript dependencies at this time
        } else if (top == 'project') {
            def projectName = callStack.first()?.arguments?.expressions?.find { it instanceof ConstantExpression }?.value as String
            if (projectName != null) {
                Project affectedProject = project.rootProject.subprojects.find { it.name == projectName.replace(':', '') }
                return [affectedProject]
            }
            log.warn("Ignoring call ${call.methodAsString} with top $top for now")
        } else {
            // at this point, there should not be any project-grouping dsl
            if (top == 'dependencies') {
                return [project]
            } else if (top == 'configurations') {
                return [project]
            } else {
                log.warn("Ignoring call ${call.methodAsString} with top $top for now")
            }
        }
        return []
    }

    @CompileStatic
    class BypassedForce {
        GradleDependency dep
        ForcedDependency forcedDependency
        String projectName

        BypassedForce(GradleDependency dep, ForcedDependency forcedDependency, String projectName) {
            this.dep = dep
            this.forcedDependency = forcedDependency
            this.projectName = projectName
        }
    }

    @CompileStatic
    class ForcedDependency {
        GradleDependency dep
        Expression forceExpression
        String declaredConfigurationName
        Collection<Configuration> resolvedConfigurations = new ArrayList<>()
        String message = ''
        String strictVersion = ''
        String forceType

        ForcedDependency(GradleDependency dep, Expression forceExpression, String declaredConfigurationName, String forceType) {
            this.dep = dep
            this.forceExpression = forceExpression
            this.declaredConfigurationName = declaredConfigurationName
            this.forceType = forceType
        }

        void setMessage(String message) {
            this.message = message
        }

        void setStrictVersion(String strictVersion) {
            this.strictVersion = strictVersion
        }
    }
}
