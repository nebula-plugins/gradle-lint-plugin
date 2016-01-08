package com.netflix.nebula.lint.rule.rename

class RenameNebulaStashRule extends PluginRenamedRule {
    RenameNebulaStashRule() {
        super('gradle-stash', 'nebula.gradle-stash')
    }
}
