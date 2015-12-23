package com.netflix.nebula.lint.rule.rename

class RenameNebulaDependencyLockRule extends PluginRenamedRule {
    RenameNebulaDependencyLockRule() {
        super('rename-dependency-lock', 'gradle-dependency-lock', 'nebula.dependency-lock')
    }
}
