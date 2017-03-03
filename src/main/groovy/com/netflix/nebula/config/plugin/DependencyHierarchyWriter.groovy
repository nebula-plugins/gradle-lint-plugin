package com.netflix.nebula.config.plugin;

class DependencyHierarchyWriter {
    String printHierarchy(Map<String, List<String>> dependencies) {
        if (dependencies.isEmpty()) return ''

        def roots = dependencies.keySet().findAll { root -> !dependencies.values().flatten().find { it == root } }

        roots.inject('') { acc, root ->
            acc += '\n\n' + printHierarchyRecurse('', root, dependencies, [:])
            acc.trim()
        }
    }

    private String printHierarchyRecurse(String out, String dep, Map<String, List<String>> dependencies,
                                         Map<String, Boolean> path) {
        def markers = []
        path.values().eachWithIndex { hasAnotherChild, i ->
            def lineMarker = (i < path.size() - 1 || hasAnotherChild) ? '|' : '\\'
            markers += hasAnotherChild || i == path.size() - 1 ? lineMarker : ' '
        }

        out += markers.join('  ')
        if (!path.isEmpty()) out += '_ '
        out += dep

        if (path.containsKey(dep))
            return out + ' (cycle)\n'

        out += '\n'

        def children = dependencies.get(dep)
        children.eachWithIndex { child, i ->
            out = printHierarchyRecurse(out, child, dependencies, path + [(dep): i < children.size() - 1])
        }

        return out
    }

    String printHierarchy(String... depStrings) {
        printHierarchy(depStrings.inject([:].withDefault { [] }) { allDeps, line ->
            def dep = line.split('->') // split into [parent, child]
            if (dep.size() == 2)
                allDeps[dep[0]] += dep[1]
            return allDeps
        })
    }
}