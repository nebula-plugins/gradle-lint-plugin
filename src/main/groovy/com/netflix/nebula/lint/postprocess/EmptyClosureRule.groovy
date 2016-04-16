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

package com.netflix.nebula.lint.postprocess

import com.netflix.nebula.lint.rule.GradleLintRule
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

class EmptyClosureRule extends GradleLintRule {
    def potentialDeletes = [] as List<MethodCallExpression>

    @Override
    void visitMethodCallExpression(MethodCallExpression call) {
        def expressions = call.arguments.expressions

        expressions.each {
            // prevents empty tasks from being deleted that take this form:
            // task taskA {}
            potentialDeletes.remove(it)
        }

        if (expressions.size == 1 && expressions.last() instanceof ClosureExpression) {
            def closure = expressions.last() as ClosureExpression
            if(closure.code.empty) {
                potentialDeletes.add(call)
            }
        }
    }

    @Override
    protected void visitClassComplete(ClassNode node) {
        potentialDeletes.unique().each {
            addLintViolation('this is an empty configuration closure that can be removed', it)
                .delete(it)
        }
    }
}
