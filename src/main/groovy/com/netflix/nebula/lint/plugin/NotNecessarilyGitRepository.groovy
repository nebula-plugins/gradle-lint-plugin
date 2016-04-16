package com.netflix.nebula.lint.plugin

import org.eclipse.jgit.attributes.AttributesNodeProvider
import org.eclipse.jgit.errors.NoWorkTreeException
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

/**
 * Repository implementation that doesn't require the root directory to actually be a git repository.
 * The git apply command which we use in the fix task is just a patch application tool that doesn't require
 * the target directory to be a git repository.
 */
class NotNecessarilyGitRepository extends Repository {
    File workTree

    NotNecessarilyGitRepository(File gitDir) {
        super(new FileRepositoryBuilder().setGitDir(gitDir).setup())
        workTree = gitDir
    }

    @Override
    File getWorkTree() throws NoWorkTreeException {
        return workTree
    }

    @Override
    void create(boolean bare) throws IOException {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    ObjectDatabase getObjectDatabase() {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    RefDatabase getRefDatabase() {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    StoredConfig getConfig() {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    AttributesNodeProvider createAttributesNodeProvider() {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    void scanForRepoChanges() throws IOException {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    void notifyIndexChanged() {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }

    @Override
    ReflogReader getReflogReader(String refName) throws IOException {
        throw new UnsupportedOperationException('This is not necessarily a git repo')
    }
}