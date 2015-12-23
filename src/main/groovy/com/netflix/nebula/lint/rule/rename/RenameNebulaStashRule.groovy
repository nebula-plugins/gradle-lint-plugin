package com.netflix.nebula.lint.rule.rename

class RenameNebulaStashRule extends PluginRenamedRule {
    RenameNebulaStashRule() {
        super('rename-stash', 'gradle-stash', 'nebula.gradle-stash')
    }
}
