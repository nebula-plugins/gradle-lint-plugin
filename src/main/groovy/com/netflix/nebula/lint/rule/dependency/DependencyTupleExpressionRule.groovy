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

import com.netflix.nebula.lint.rule.GradleAstUtil
import com.netflix.nebula.lint.rule.GradleDependency
import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradleModelAware
import org.codehaus.groovy.ast.expr.MethodCallExpression

class DependencyTupleExpressionRule extends GradleLintRule implements GradleModelAware {
    String description = "use the more compact string representation of a dependency when possible"

    @Override
    void visitAnyGradleDependency(MethodCallExpression call, String conf, GradleDependency dep) {
        if(dep.conf == null && dep.syntax == GradleDependency.Syntax.MapNotation) {
            // FIXME what if one of the values is a function call?
            def ex = GradleAstUtil.collectEntryExpressions(call)
            addBuildLintViolation('use the shortcut form of the dependency', call)
                .replaceWith(call, "${call.methodAsString} '${ex.group ?: ''}:${ex.name}${ex.version ? ":$ex.version" : ''}'")
        }
    }
}
