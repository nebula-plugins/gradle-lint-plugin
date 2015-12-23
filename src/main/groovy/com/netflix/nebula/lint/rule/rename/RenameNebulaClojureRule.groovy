package com.netflix.nebula.lint.rule.rename

class RenameNebulaClojureRule extends PluginRenamedRule {
    RenameNebulaClojureRule() {
        super('rename-clojure', 'nebula-clojure', 'nebula.clojure')
    }
}