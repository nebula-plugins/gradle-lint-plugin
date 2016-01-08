package com.netflix.nebula.lint.rule.rename

class RenameNebulaIntegTestRule extends PluginRenamedRule {
    RenameNebulaIntegTestRule() {
        super('nebula-integtest', 'nebula.integtest')
    }
}