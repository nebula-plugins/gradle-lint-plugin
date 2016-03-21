package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.compile.AbstractCompile

import java.util.jar.JarEntry
import java.util.jar.JarFile

class DependencyUtils {
    static Set<String> classes(ResolvedDependency d) {
        def definedClasses = new HashSet<String>()

        d.moduleArtifacts
                .collect { it.file }
                .findAll { it.name.endsWith('.jar') }
                .findAll { !it.name.endsWith('-sources.jar') && !it.name.endsWith('-javadoc.jar') }
                .each {
            def jarFile = new JarFile(it)
            def allEntries = jarFile.entries()
            while (allEntries.hasMoreElements()) {
                def entry = allEntries.nextElement() as JarEntry
                if(entry.name.endsWith('.class'))
                    definedClasses += entry.name.replaceAll(/\.class$/, '')
            }
            jarFile.close()
        }

        return definedClasses
    }

    static Set<String> configurations(ResolvedDependency d, Project p) {
        return (p.configurations as Collection<Configuration>).findAll { it.resolvedConfiguration
                .firstLevelModuleDependencies.contains(d) }*.name
    }

    static ClassLoader compiledSourceClassLoader(Project p) {
        def jars = p.configurations*.resolvedConfiguration*.getFiles(Specs.SATISFIES_ALL).flatten()
                .findAll { it.name.endsWith('.jar') } as List<File>

        def classDirs = p.tasks.findAll { it instanceof AbstractCompile }
                .collect { it as AbstractCompile }
                .collect { it.destinationDir }
                .unique()
                .findAll { it.exists() }

        return new URLClassLoader((jars + classDirs).collect { it.toURI().toURL() } as URL[])
    }
}
