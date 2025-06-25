package com.netflix.nebula.lint

import nebula.test.IntegrationTestKitSpec

abstract class BaseIntegrationTestKitSpec extends IntegrationTestKitSpec {
    def setup() {
        // Enable configuration cache :)
        new File(projectDir, 'gradle.properties') << '''org.gradle.configuration-cache=true'''.stripIndent()

        gradleVersion = '9.0.0-rc-1'
    }


    void disableConfigurationCache() {
        def propertiesFile = new File(projectDir, 'gradle.properties')
        if(propertiesFile.exists()) {
            propertiesFile.delete()
        }
        propertiesFile.createNewFile()
        propertiesFile << '''org.gradle.configuration-cache=false'''.stripIndent()
    }
}
