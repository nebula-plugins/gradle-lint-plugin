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

package com.netflix.nebula.lint

import static java.nio.file.Files.isSymbolicLink

enum FileMode {
    Regular(100644),
    Symlink(120000),
    Executable(100755)

    int mode

    FileMode(int mode) {
        this.mode = mode
    }

    static fromFile(File file) {
        if (isSymbolicLink(file.toPath()))
            return Symlink
        else if (file.canExecute()) {
            return Executable
        } else {
            return Regular
        }
    }
}
