package com.netflix.nebula.lint.rule.rename

class RenameNebulaGitScmRule extends PluginRenamedRule {
    RenameNebulaGitScmRule() {
        super('rename-git-scm', 'gradle-git-scm', 'nebula.gradle-git-scm')
    }
}
