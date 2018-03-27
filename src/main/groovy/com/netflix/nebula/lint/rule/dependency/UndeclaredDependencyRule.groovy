package com.netflix.nebula.lint.rule.dependency

import com.google.common.collect.ImmutableMap
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.plugins.JavaPluginConvention

class UndeclaredDependencyRule extends GradleLintRule implements GradleModelAware {
    private static final String SUBPROJECTS_BLOCK = 'subprojectsBlock'
    private static final String ALLPROJECTS_BLOCK = 'allprojectsBlock'
    private static final String ROOT_BLOCK = 'rootBlock'
    private static final String SUBPROJECTS_DEPENDENCIES_BLOCK = 'subprojectsDependenciesBlock'
    private static final String ALLPROJECTS_DEPENDENCIES_BLOCK = 'allprojectsDependenciesBlock'
    private static final String ROOT_DEPENDENCIES_BLOCK = 'rootDependenciesBlock'
    private static final List<String> BLOCKS_TO_SEARCH_IN = [
            ALLPROJECTS_BLOCK,
            SUBPROJECTS_BLOCK,
            ROOT_BLOCK
    ]
    private static final Map<String, String> DEPENDENCY_BLOCK_ASSOCIATIONS = ImmutableMap.builder()
            .put(ALLPROJECTS_BLOCK, ALLPROJECTS_DEPENDENCIES_BLOCK)
            .put(SUBPROJECTS_BLOCK, SUBPROJECTS_DEPENDENCIES_BLOCK)
            .put(ROOT_BLOCK, ROOT_DEPENDENCIES_BLOCK)
            .build()

    String description = 'Ensure that directly used transitives are declared as first order dependencies, for Gradle versions 4.5 - 5'
    DependencyService dependencyService

    @Override
    protected void beforeApplyTo() {
        dependencyService = DependencyService.forProject(project)
    }

    @Override
    void visitAllprojects(MethodCallExpression call) {
        bookmark(ALLPROJECTS_BLOCK, call)
    }

    @Override
    void visitSubprojects(MethodCallExpression call) {
        bookmark(SUBPROJECTS_BLOCK, call)
    }

    @Override
    void visitAllprojectsGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        bookmark(ALLPROJECTS_DEPENDENCIES_BLOCK, call)
    }

    @Override
    void visitSubprojectGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        bookmark(SUBPROJECTS_DEPENDENCIES_BLOCK, call)
    }

    @Override
    void visitClass(ClassNode classNode) {
        bookmark(ROOT_BLOCK, classNode)
    }

    @Override
    void visitDependencies(MethodCallExpression call) {
        def parentNode = parentNode()
        if (parentIs(parentNode, 'allprojects')) {
            bookmark(ALLPROJECTS_DEPENDENCIES_BLOCK, call)
        } else if (parentIs(parentNode, 'subprojects')) {
            bookmark(SUBPROJECTS_DEPENDENCIES_BLOCK, call)
        } else if (parentNode == null) {
            bookmark(ROOT_DEPENDENCIES_BLOCK, call)
        }
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        Set<ModuleVersionIdentifier> insertedDependencies = [] as Set
        Map<String, HashMap<String, ASTNode>> violations = new HashMap()

        BLOCKS_TO_SEARCH_IN.each { currentBlock ->
            if (bookmark(currentBlock) != null) {
                def convention = project.convention.findPlugin(JavaPluginConvention)
                if (convention != null) {
                    // sort the sourceSets from least dependent to most dependent, e.g. [main, test, integTest]
                    def sortedSourceSets = convention.sourceSets.sort(false, dependencyService.sourceSetComparator())

                    sortedSourceSets.each { sourceSet ->
                        def confName = sourceSet.compileConfigurationName
                        violations.put(confName, new HashMap())

                        dependencyService.undeclaredDependencies(confName).each { undeclared ->
                            // only add the dependency in the lowest configuration that requires it
                            if (insertedDependencies.add(undeclared)) {
                                def dependencyBlock = bookmark(DEPENDENCY_BLOCK_ASSOCIATIONS.get(currentBlock))
                                if (dependencyBlock != null) {

                                    // collect violations for handling later
                                    HashMap<String, ASTNode> violationsForConfig = violations.get(confName)
                                    violationsForConfig.put(undeclared.toString(), dependencyBlock)

                                    violations.put(confName, violationsForConfig)
                                } else if (parentNode() == null) {
                                    // there is no dependencies block, and this is not in an allprojects or subprojects block
                                    addBuildLintViolation("one or more classes in $undeclared are required by your code directly, " +
                                            "and you require a dependencies block in your subproject $project")
                                            .insertAfter(project.buildFile, 0, """\
                                        dependencies {
                                        }
                                        """.stripIndent())
                                }
                            }
                        }
                    }
                }
            }
        }

        addUndeclaredDependenciesAlphabetically(violations)
    }

    private void addUndeclaredDependenciesAlphabetically(Map<String, Map> violations) {
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
                        .insertIntoClosureAtTheEnd(dependencyBlock, "$configurationName '$undeclaredDependency'")
            }
        }
    }

    private static Boolean parentIs(Expression parentNode, String parentAsString) {
        parentNode instanceof MethodCallExpression && ((MethodCallExpression) parentNode).methodAsString == parentAsString
    }
}
