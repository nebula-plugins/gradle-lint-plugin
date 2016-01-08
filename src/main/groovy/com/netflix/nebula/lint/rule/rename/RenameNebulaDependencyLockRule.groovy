package com.netflix.nebula.lint.rule.rename

class RenameNebulaDependencyLockRule extends PluginRenamedRule {
    RenameNebulaDependencyLockRule() {
        super('gradle-dependency-lock', 'nebula.dependency-lock')
    }
}
