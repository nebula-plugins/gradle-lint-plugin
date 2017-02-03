/**
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.nebula.lint.rule.wrapper

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.invocation.DefaultGradle
import org.gradle.util.GradleVersion

/**
 * Created by Boaz Jan on 22/05/16.
 */
class CustomVersionGradle extends DefaultGradle {
    GradleVersion version = GradleVersion.current()

    public CustomVersionGradle(GradleInternal parent, StartParameter startParameter, ServiceRegistryFactory parentRegistry) {
        super(parent, startParameter, parentRegistry)
    }

    @Override
    String getGradleVersion() {
        return version.getVersion()
    }
}
