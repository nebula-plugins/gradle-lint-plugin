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

package com.netflix.nebula.lint.rule

import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codenarc.source.SourceCode

class GradleAstUtil {
    static Map<String, String> collectEntryExpressions(MethodCallExpression call, SourceCode originalSource = null) {
        call.arguments.expressions
                .findAll { it instanceof MapExpression }
                .collect { it.mapEntryExpressions }
                .flatten()
                .collectEntries { MapEntryExpression entry -> [entry.keyExpression.text, extractValue(entry, originalSource)] } as Map<String, String>
    }

    private static String extractValue(MapEntryExpression entry, SourceCode originalSource) {
        def value = entry.valueExpression
        //for one line declaration we try to be more precise and extract original source code since `.text` can be lossy for GString expressions
        if (originalSource != null && value.lineNumber == value.lastLineNumber)
            originalSource.lines.get(value.lineNumber - 1).substring(value.columnNumber, value.lastColumnNumber - 2)
        else
            entry.valueExpression.text
    }
}
