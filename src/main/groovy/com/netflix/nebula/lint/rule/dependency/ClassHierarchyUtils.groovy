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

import groovy.transform.Memoized
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClassHierarchyUtils {
    private static Logger logger = LoggerFactory.getLogger(ClassHierarchyUtils)

    /**
     * @return All types in the type hierarchy, including parameterizations at every level
     */
    @Memoized
    static Collection<String> typeHierarchy(Class<?> clazz) {
        try {
            return typeHierarchyRecursive(clazz) - clazz.name
        } catch(Throwable t) {
            logger.debug("Unable to load super type or interfaces", t)
            return []
        }
    }

    private static Collection<String> typeHierarchyRecursive(Class<?> clazz) {
        if(clazz.name.startsWith('java.'))
            return []

        return (clazz.superclass ? typeHierarchyRecursive(clazz.superclass) : []) +
                (clazz.interfaces.collect { typeHierarchyRecursive(it) }.flatten() as Collection<String>) +
                clazz.name
    }
}
