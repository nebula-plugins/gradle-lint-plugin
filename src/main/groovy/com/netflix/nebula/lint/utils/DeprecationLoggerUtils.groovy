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
package com.netflix.nebula.lint.utils

import groovy.transform.CompileDynamic
import org.gradle.api.GradleException
import org.gradle.util.GradleVersion

import java.lang.reflect.InvocationTargetException

@CompileDynamic
class DeprecationLoggerUtils {
    private static final String LEGACY_DEPRECATION_LOGGER_CLASS = 'org.gradle.util.DeprecationLogger'
    private static final String DEPRECATION_LOGGER_CLASS = 'org.gradle.internal.deprecation.DeprecationLogger'

    static void whileDisabled(Runnable action) {
        String gradleDeprecationLoggerClassName = (GradleVersion.current() >= GradleVersion.version('6.2') || GradleVersion.current().version.startsWith('6.2')) ? DEPRECATION_LOGGER_CLASS : LEGACY_DEPRECATION_LOGGER_CLASS
        try {
            Class clazz = Class.forName(gradleDeprecationLoggerClassName)
            clazz.getMethod("whileDisabled", Runnable).invoke(this, action)
        } catch (ClassNotFoundException e) {
            throw new GradleException("Could not execute whileDisabled runnable action for $gradleDeprecationLoggerClassName | $e.message", e)
        } catch (InvocationTargetException e) {
            throw e.targetException
        }
    }
}