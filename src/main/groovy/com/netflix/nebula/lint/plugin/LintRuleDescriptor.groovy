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

package com.netflix.nebula.lint.plugin

import groovy.transform.Canonical
import org.gradle.api.UncheckedIOException

import javax.annotation.Nullable

@Canonical
class LintRuleDescriptor {
    URL propertiesFileUrl

    String getImplementationClassName() {
        loadProperties(propertiesFileUrl).getProperty('implementation-class')
    }

    List<String> getIncludes() {
        loadProperties(propertiesFileUrl).getProperty('includes')?.split(',') ?: [] as List<String>
    }

    private  static Properties loadProperties(URL url) {
        try {
            URLConnection uc = url.openConnection()
            uc.setUseCaches(false)
            return loadProperties(uc.inputStream)
        } catch (IOException e) {
            throw new UncheckedIOException(e)
        }
    }

    private static Properties loadProperties(InputStream inputStream) {
        Properties properties = new Properties()
        try {
            properties.load(inputStream)
        } catch (IOException e) {
            throw new UncheckedIOException(e)
        } finally {
            closeQuietly(inputStream)
        }

        return properties
    }

    private static void closeQuietly(@Nullable Closeable resource) {
        try {
            if (resource != null) {
                resource.close()
            }
        } catch (IOException e) {
        }

    }
}
