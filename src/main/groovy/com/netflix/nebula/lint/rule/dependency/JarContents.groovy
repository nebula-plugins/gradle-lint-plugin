package com.netflix.nebula.lint.rule.dependency

class JarContents {
    Set<String> entryNames

    /**
     * Fully qualified class names with '/' package separators
     */
    @Lazy Set<String> classes = entryNames.findAll { it.endsWith('.class') }
            .collect { it.replaceAll(/\.class$/, '') }.toSet()

    @Lazy boolean isServiceProvider = entryNames.any { it == 'META-INF/services/' }
    @Lazy boolean nothingButMetaInf = !entryNames.any { !it.startsWith('META-INF') }
    @Lazy boolean isWebjar = entryNames.any { it == 'META-INF/resources/webjars/' }
}
