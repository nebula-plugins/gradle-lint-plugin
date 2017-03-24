package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractDuplicateDependencyClassRule extends GradleLintRule implements GradleModelAware {
    final Logger logger = LoggerFactory.getLogger(AbstractDuplicateDependencyClassRule)

    String description = 'classpaths with duplicate classes may break unpredictably depending on the order in which dependencies are provided to the classpath'

    Set<Configuration> directlyUsedConfigurations = [] as Set
    Set<ModuleVersionIdentifier> ignoredDependencies = [] as Set

    abstract protected List<ModuleVersionIdentifier> moduleIds(Configuration conf)

    protected static List<ModuleVersionIdentifier> firstOrderModuleIds(Configuration conf) {
        return conf.resolvedConfiguration.firstLevelModuleDependencies.collect { it.module.id }
    }

    protected static List<ModuleVersionIdentifier> transitiveModuleIds(Configuration conf) {
        // Classifier artifacts (javadoc/sources/etc.) can sometimes be resolved implicitly causing duplicates, we're interested only in distinct modules
        return conf.resolvedConfiguration.resolvedArtifacts
                .collect { it.moduleVersion.id }
                .unique { it.module.toString() }
    }

    @Override
    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if (ignored) {
            ignoredDependencies.add(dep.toModuleVersion())
        } else {
            def confModel = project.configurations.findByName(conf)
            if (confModel) {
                directlyUsedConfigurations.add(confModel)
            }
        }
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        for (Configuration conf : directlyUsedConfigurations) {
            if (DependencyService.forProject(project).isResolved(conf)) {
                checkForDuplicates(conf)
            }
        }
    }

    protected void checkForDuplicates(Configuration conf) {
        moduleIds(conf).each {
            checkForDuplicates(it, conf.name)
        }
    }

    protected void checkForDuplicates(ModuleVersionIdentifier mvid, String conf) {
        def dependencyService = DependencyService.forProject(project)
        if (ignoredDependencies.contains(mvid))
            return

        def dependencyClasses = dependencyService.jarContents(mvid.module)?.classes
        if (!dependencyClasses)
            return

        def dupeDependencyClasses = dependencyService.artifactsByClass(conf)
                .findAll {
            // don't count artifacts that have the same ModuleIdentifier, which are different versions of the same
            // module coming from extended configurations that are ultimately conflict resolved away anyway
            Collection<ResolvedArtifact> artifacts = it.value
            dependencyClasses.contains(it.key) && artifacts.any {
                !ignoredDependencies.contains(it.moduleVersion.id) && it.moduleVersion.id.module != mvid.module
            }
        }

        def dupeClassesByDependency = new TreeMap<ModuleVersionIdentifier, Set<String>>(DependencyService.DEPENDENCY_COMPARATOR).withDefault {
            [] as Set
        }
        dupeDependencyClasses.each { className, resolvedArtifacts ->
            resolvedArtifacts.each { artifact ->
                dupeClassesByDependency.get(artifact.moduleVersion.id).add(className)
            }
        }

        def configuration = project.configurations.getByName(conf)
        if (!dupeClassesByDependency.isEmpty() && mvid == dupeClassesByDependency.keySet().first()) {
            dupeClassesByDependency.each { resolvedMvid, classes ->
                if (mvid != resolvedMvid) {
                    def message = "$mvid in $configuration has ${classes.size()} classes duplicated by ${resolvedMvid}"
                    logger.debug("$message. Duplicate classes:\n$classes")
                    addBuildLintViolation("$message (use --debug for detailed class list)")
                }
            }
        }
    }
}
