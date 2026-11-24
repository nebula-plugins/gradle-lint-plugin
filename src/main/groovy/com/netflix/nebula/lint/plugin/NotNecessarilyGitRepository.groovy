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

import org.eclipse.jgit.attributes.AttributesNodeProvider
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.errors.NoWorkTreeException
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

import static org.eclipse.jgit.util.FS.DETECTED

/**
 * Repository implementation that doesn't require the root directory to actually be a git repository.
 * The git apply command which we use in the fix task is just a patch application tool that doesn't require
 * the target directory to be a git repository.
 */
class NotNecessarilyGitRepository extends Repository {
    File workTree
    private final InMemoryRepository metadata = new InMemoryRepository(new DfsRepositoryDescription())

    NotNecessarilyGitRepository(File gitDir) {
        super(new FileRepositoryBuilder().setGitDir(gitDir).setFS(DETECTED));
        workTree = gitDir

    }

    @Override
    File getWorkTree() throws NoWorkTreeException {
        return workTree
    }

    @Override
    String getIdentifier() {
        return workTree.absolutePath
    }

    @Override
    DirCache readDirCache() throws IOException {
        // Applying lint fixes must not depend on, or modify, a consumer's Git index.
        return DirCache.newInCore()
    }

    @Override
    void create(boolean bare) throws IOException {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    ObjectDatabase getObjectDatabase() {
        return metadata.objectDatabase
    }

    @Override
    RefDatabase getRefDatabase() {
        return metadata.refDatabase
    }

    @Override
    StoredConfig getConfig() {
        return metadata.config
    }

    @Override
    AttributesNodeProvider createAttributesNodeProvider() {
        return metadata.createAttributesNodeProvider()
    }

    @Override
    void scanForRepoChanges() throws IOException {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    void notifyIndexChanged(boolean internal) {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    ReflogReader getReflogReader(String refName) throws IOException {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }
}
