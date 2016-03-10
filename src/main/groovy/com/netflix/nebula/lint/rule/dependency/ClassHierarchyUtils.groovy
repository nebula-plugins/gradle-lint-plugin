package com.netflix.nebula.lint.rule.dependency

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClassHierarchyUtils {
    private static Logger logger = LoggerFactory.getLogger(ClassHierarchyUtils)

    /**
     * @return All types in the type hierarchy, including parameterizations at every level
     */
    static Collection<String> typeHierarchy(Class<?> clazz) {
        try {
            return typeHierarchyRecursive(clazz) - clazz.name
        } catch(Throwable t) {
            logger.debug("Unable to load super type or interfaces of $clazz.name")
            return []
        }
    }

    private static Collection<String> typeHierarchyRecursive(Class<?> clazz) {
        if(clazz.name.startsWith('java.'))
            return []

        return (clazz.superclass ? typeHierarchyRecursive(clazz.superclass) : []) +
                (clazz.interfaces.collect { typeHierarchyRecursive(it) }.flatten() as Collection<String>) +
                clazz.name
    }
}
