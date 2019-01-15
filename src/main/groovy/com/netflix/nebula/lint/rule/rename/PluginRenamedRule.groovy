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

package com.netflix.nebula.lint.rule.rename

import com.netflix.nebula.lint.rule.GradleLintRule
import com.netflix.nebula.lint.rule.GradlePlugin
import groovy.transform.Canonical
import org.codehaus.groovy.ast.expr.MethodCallExpression

@Canonical
class PluginRenamedRule extends GradleLintRule {
    String deprecatedPluginName
    String pluginName

    @Override
    String getDescription() {
        return "the plugin name $deprecatedPluginName has been deprecated in favor of $pluginName"
    }

    @Override
    void visitApplyPlugin(MethodCallExpression call, String plugin) {
        if(plugin == deprecatedPluginName) {
            addBuildLintViolation("plugin $deprecatedPluginName has been renamed to $pluginName", call)
                .replaceWith(call, "apply plugin: '$pluginName'")

        }
    }

    @Override
    void visitGradlePlugin(MethodCallExpression call, String conf, GradlePlugin plugin) {
        if (plugin.id == deprecatedPluginName) {
            addBuildLintViolation("plugin $deprecatedPluginName has been renamed to $pluginName", call)
                .replaceWith(call, "id '$pluginName'")
        }
    }
}