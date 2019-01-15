/*
 * Copyright 2015-2019 Netflix, Inc.
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

package com.netflix.nebula.lint.postprocess

import com.netflix.nebula.lint.rule.GradleLintRule
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression

/**
 * DSL blocks made empty by the deletion of their contents by other rules should be purged altogether.
 * This rule cannot distinguish between blocks made empty by other rules and those that were already empty,
 * but (while imperfect) doesn't do any harm by removing unused cruft incidentally.
 */
class EmptyClosureRule extends GradleLintRule {
    String description = 'empty closures should be removed'

    def emptyClosureCalls = [] as List<MethodCallExpression>
    def taskNames = [] as List<Expression>
    List<String> deletableBlocks = []
    Boolean enableDeletableBlocks = false

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        def expressions = call.arguments.expressions

        expressions.each {
            // prevents empty tasks from being deleted that take this form:
            // task taskA {}
            taskNames.add(it)
        }

        if (isDeletable(call.methodAsString) && expressions.size == 1 && expressions.last() instanceof ClosureExpression) {
            if(expressions.last().code.empty) {
                emptyClosureCalls.add(call)
            }
        }
    }

    protected Boolean isDeletable(String block) {
        !enableDeletableBlocks || (enableDeletableBlocks && deletableBlocks.contains(block))
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        (emptyClosureCalls - taskNames).unique().each {
            addBuildLintViolation('this is an empty configuration closure that can be removed', it)
                .delete(it)
        }
    }
}
