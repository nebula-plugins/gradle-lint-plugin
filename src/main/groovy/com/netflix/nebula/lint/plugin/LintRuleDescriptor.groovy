package com.netflix.nebula.lint.plugin

import groovy.transform.Canonical
import org.gradle.util.GUtil

@Canonical
class LintRuleDescriptor {
    URL propertiesFileUrl

    String getImplementationClassName() {
        GUtil.loadProperties(propertiesFileUrl).getProperty('implementation-class')
    }

    List<String> getIncludes() {
        GUtil.loadProperties(propertiesFileUrl).getProperty('includes')?.split(',') ?: [] as List<String>
    }
}
