package com.netflix.nebula.lint

import nebula.test.IntegrationTestKitSpec

abstract class BaseIntegrationTestKitSpec extends IntegrationTestKitSpec {

    void disableConfigurationCache() {
        def propertiesFile = new File(projectDir, 'gradle.properties')
        if(propertiesFile.exists()) {
            propertiesFile.delete()
        }
        propertiesFile.createNewFile()
        propertiesFile << '''org.gradle.configuration-cache=false'''.stripIndent()
    }
}
