package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class DuplicateDependencyService(val project: Project) {
    companion object {
        val BLACKLISTED_CLASSES = setOf(
                "package-info",
                "module-info"
        )
    }

    val logger: Logger = LoggerFactory.getLogger(DuplicateDependencyService::class.java)

    fun violationsForModules(moduleIds: List<ModuleVersionIdentifier>, conf: Configuration, ignoredDependencies: Set<ModuleVersionIdentifier>): List<String> =
            moduleIds.flatMap { violationsForModule(it, conf.name, ignoredDependencies) }

    fun violationsForModule(mvid: ModuleVersionIdentifier, conf: String, ignoredDependencies: Set<ModuleVersionIdentifier>): List<String> {
        val dependencyService = DependencyService.forProject(project)
        if (ignoredDependencies.contains(mvid)) {
            return emptyList()
        }

        val dependencyClasses = dependencyService.jarContents(mvid.module)?.classes ?: return emptyList()
        val dupeDependencyClasses = dependencyService.artifactsByClass(conf)
                .filter {
                    !BLACKLISTED_CLASSES.contains(it.key)
                }
                .filter {
                    // don't count artifacts that have the same ModuleIdentifier, which are different versions of the same
                    // module coming from extended configurations that are ultimately conflict resolved away anyway
                    val artifacts = it.value
                    dependencyClasses.contains(it.key) && artifacts.any {
                        !ignoredDependencies.contains(it.moduleVersion.id) && it.moduleVersion.id.module != mvid.module
                    }
                }

        val dupeClassesByDependency = TreeMap<ModuleVersionIdentifier, MutableSet<String>>(DependencyService.DEPENDENCY_COMPARATOR)
        dupeDependencyClasses.forEach { (className, resolvedArtifacts) ->
            resolvedArtifacts.forEach { artifact ->
                val moduleId = artifact.moduleVersion.id
                if (!dupeClassesByDependency.containsKey(moduleId)) {
                    dupeClassesByDependency.put(moduleId, mutableSetOf<String>())
                }
                dupeClassesByDependency[artifact.moduleVersion.id]!!.add(className)
            }
        }

        val violations = mutableListOf<String>()
        val configuration = project.configurations.getByName(conf)
        if (!dupeClassesByDependency.isEmpty() && mvid == dupeClassesByDependency.keys.first()) {
            dupeClassesByDependency.forEach { (resolvedMvid, classes) ->
                if (mvid != resolvedMvid) {
                    val message = "$mvid in $configuration has ${classes.size} classes duplicated by $resolvedMvid"
                    logger.info("$message. Duplicate classes: $classes")
                    violations.add("$message (use --info for detailed class list)")
                }
            }
        }
        return violations
    }
}
