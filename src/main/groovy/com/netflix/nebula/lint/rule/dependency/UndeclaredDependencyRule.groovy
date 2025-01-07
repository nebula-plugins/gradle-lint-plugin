package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.SourceSetUtils
import com.netflix.nebula.lint.rule.ModelAwareGradleLintRule
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ModuleVersionIdentifier

@CompileStatic
class UndeclaredDependencyRule extends ModelAwareGradleLintRule {
    private static final String DEPENDENCIES_BLOCK = 'rootDependenciesBlock'
    String description = 'Ensure that directly used transitives are declared as first order dependencies'
    DependencyService dependencyService

    @Override
    protected void beforeApplyTo() {
        dependencyService = DependencyService.forProject(project)
    }

    @Override
    void visitDependencies(MethodCallExpression call) {
        def parentNode = parentNode()
        if (parentNode == null) {
            //bookmark only current project dependencies (not allprojects and subprojects)
            bookmark(DEPENDENCIES_BLOCK, call)
        }
    }

    @Override
    void visitClassComplete(ClassNode node) {
        Set<ModuleVersionIdentifier> insertedDependencies = [] as Set
        Map<String, HashMap<String, ASTNode>> violations = new HashMap()

        if (SourceSetUtils.hasSourceSets(project)) {
            // sort the sourceSets from least dependent to most dependent, e.g. [main, test, integTest]
            def sortedSourceSets = SourceSetUtils.getSourceSets(project).sort(false, dependencyService.sourceSetComparator())

            sortedSourceSets.each { sourceSet ->
                def confName = sourceSet.compileClasspathConfigurationName
                violations.put(confName, new HashMap())

                def undeclaredDependencies = dependencyService.undeclaredDependencies(confName)
                def filteredUndeclaredDependencies = filterUndeclaredDependencies(undeclaredDependencies, confName)

                if (!filteredUndeclaredDependencies.isEmpty()) {
                    def dependencyBlock = bookmark(DEPENDENCIES_BLOCK)
                    if (dependencyBlock != null) {
                        filteredUndeclaredDependencies.each { undeclared ->
                            // only add the dependency in the lowest configuration that requires it
                            if (insertedDependencies.add(undeclared)) {
                                // collect violations for handling later
                                HashMap<String, ASTNode> violationsForConfig = violations.get(confName)
                                violationsForConfig.put(undeclared.toString(), dependencyBlock)
                                violations.put(confName, violationsForConfig)
                            }
                        }
                    } else {
                        // there is no dependencies block so we need a new one
                        addBuildLintViolation("one or more classes are required by your code directly, " +
                                "and you require a dependencies block in your subproject $project")
                                .insertAfter(project.buildFile, 0, """\
                                    dependencies {
                                    }
                                    """.stripIndent())
                    }
                }
            }
        }

        addUndeclaredDependenciesAlphabetically(violations)
    }

    Set<ModuleVersionIdentifier> filterUndeclaredDependencies(Set<ModuleVersionIdentifier> undeclaredDependencies, String configurationName) {
        return undeclaredDependencies
    }

    @CompileDynamic
    private void addUndeclaredDependenciesAlphabetically(Map<String, HashMap<String, ASTNode>> violations) {
        TreeMap<String, Map> sortedViolations = new TreeMap<String, Map>()
        sortedViolations.putAll(violations)

        for (Map.Entry<String, Map<String, ASTNode>> entry : sortedViolations.entrySet()) {
            String configurationName = entry.getKey()
            TreeMap sortedDependenciesAndBlocks = new TreeMap<String, ASTNode>()
            sortedDependenciesAndBlocks.putAll(entry.getValue())

            for (Map.Entry<String, ASTNode> dependencyAndBlock : sortedDependenciesAndBlocks.entrySet()) {
                String undeclaredDependency = dependencyAndBlock.getKey()
                ASTNode dependencyBlock = dependencyAndBlock.getValue()

                addBuildLintViolation("one or more classes in $undeclaredDependency are required by your code directly")
                        .insertIntoClosureAtTheEnd(dependencyBlock, "${declarationConfigurationName(configurationName)} '$undeclaredDependency'")
            }
        }
    }

    private static String declarationConfigurationName(String configName) {
        return configName
                .replace('compileClasspath', 'implementation')
                .replace('CompileClasspath', 'Implementation')
    }
}
