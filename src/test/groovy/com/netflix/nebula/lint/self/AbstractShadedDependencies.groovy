/**
 *
 *  Copyright 2020 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.netflix.nebula.lint.self

abstract trait AbstractShadedDependencies {
    Collection<ShadedCoordinate> shadedCoordinates = [
            new ShadedCoordinate('org.eclipse.jdt', 'com.netflix.nebula.lint.jdt', 'org.eclipse.jdt', 'core'),
            new ShadedCoordinate('org.eclipse.jgit', 'com.netflix.nebula.lint.jgit', 'org.eclipse.jgit', 'org.eclipse.jgit'),
            new ShadedCoordinate('org.apache.commons.lang', 'com.netflix.nebula.lint.commons.lang', 'commons-lang', 'commons-lang'),
            new ShadedCoordinate('org.codenarc', 'com.netflix.nebula.lint.org.codenarc', 'org.codenarc', 'CodeNarc'),
            new ShadedCoordinate('org.objectweb.asm', 'com.netflix.nebula.lint.org.objectweb.asm', 'org.ow2.asm', 'asm'),
            new ShadedCoordinate('org.objectweb.asm.commons', 'com.netflix.nebula.lint.org.objectweb.asm.commons', 'org.ow2.asm', 'asm-commons')
    ]
}
