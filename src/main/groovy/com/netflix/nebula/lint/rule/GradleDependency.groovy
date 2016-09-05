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

import groovy.transform.Canonical
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

@Canonical
class GradleDependency {
    String group
    String name
    String version
    String classifier
    String ext
    String conf // no way to express a conf in string notation
    Syntax syntax

    enum Syntax {
        MapNotation,
        StringNotation
    }

    ModuleVersionIdentifier toModuleVersion() {
        return new DefaultModuleVersionIdentifier(group, name, version)
    }
    
    ModuleIdentifier toModule() {
        return new DefaultModuleIdentifier(group, name)
    }

//    group:name:version:classifier@extension
    String toNotation() {
        def notation = (group ?: '') + ':'
        notation += name
        if(version) notation += ":$version"
        if(classifier) notation += ":$classifier"
        if(ext) notation += "@$ext"
        
        return notation
    }
}