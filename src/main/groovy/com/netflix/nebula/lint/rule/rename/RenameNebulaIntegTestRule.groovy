package com.netflix.nebula.lint.rule.rename

class RenameNebulaIntegTestRule extends PluginRenamedRule {
    RenameNebulaIntegTestRule() {
        super('rename-integtest', 'nebula-integtest', 'nebula.integtest')
    }
}