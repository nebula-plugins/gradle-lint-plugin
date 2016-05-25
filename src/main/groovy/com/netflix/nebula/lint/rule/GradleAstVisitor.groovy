/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement

interface GradleAstVisitor {
    void visitApplyPlugin(MethodCallExpression call, String plugin)

    void visitGradleDependency(MethodCallExpression call, String conf, GradleDependency dep)

    void visitConfigurationExclude(MethodCallExpression call, String conf, GradleDependency exclude)

    /**
     * Visit potential extension properties.  Because of the ambiguity inherent in the shared DSL
     * syntax for internal Gradle Handler objects (e.g. DependencyHandler) and extension objects,
     * this method will be triggered for method calls and property assignments on Handlers as well.
     * As long as you are overriding this visitor to look for a specific extension name and property,
     * this ambiguity will not cause problems.
     *
     * @param expression - a MethodCallExpression or BinaryExpression
     * @param extension - extension object name as rendered in the DSL
     * @param prop - property target on the extension object
     * @param value - value to assign to the extension property
     */
    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop, String value)

    /**
     * Visit potential extension properties.  Because of the ambiguity inherent in the shared DSL
     * syntax for internal Gradle Handler objects (e.g. DependencyHandler) and extension objects,
     * this method will be triggered for method calls and property assignments on Handlers as well.
     * As long as you are overriding this visitor to look for a specific extension name and property,
     * this ambiguity will not cause problems.
     *
     * @param expression - a MethodCallExpression or BinaryExpression
     * @param extension - extension object name as rendered in the DSL
     * @param prop - property target on the extension object
     */
    void visitExtensionProperty(ExpressionStatement expression, String extension, String prop)

    MethodCallExpression parentClosure()
    List<MethodCallExpression> closureStack()

    void bookmark(String label, ASTNode node)

    ASTNode bookmark(String label)

    void visitDependencies(MethodCallExpression call)

    void visitTask(MethodCallExpression call, String name, Map<String, String> args)
}