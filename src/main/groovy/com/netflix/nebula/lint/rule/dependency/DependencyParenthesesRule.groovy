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

package com.netflix.nebula.lint.rule.dependency

import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression

class DependencyParenthesesRule extends GradleLintRule implements GradleModelAware {
    String description = "don't put parentheses around dependency definitions unless it is necessary"

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        def args = call.arguments.expressions as List
        if(!args.empty && !(args[-1] instanceof ClosureExpression)) {
            def callSource = getSourceCode().line(call.lineNumber-1)
            def matcher = callSource =~ /^${call.methodAsString}\s*\((?<dep>[^\)]+)/
            if(matcher.find()) {
                addBuildLintViolation('parentheses are unnecessary for dependencies', call)
                    .replaceWith(call, "${call.methodAsString} ${matcher.group('dep')}")
            }
        }
    }
}