/*
 *
 *  Copyright 2018-2019 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.netflix.nebula.lint.rule.dependency.provider;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.specs.Specs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class ClasspathBasedRecommendationProvider extends AbstractRecommendationProvider {
    private final Logger log = LoggerFactory.getLogger(ClasspathBasedRecommendationProvider.class);
    protected Project project;

    ClasspathBasedRecommendationProvider(Project project) {
        this.project = project;
    }

    Set<File> getBomsOnConfiguration() {
        Set<File> boms = new LinkedHashSet<>(); // to preserve insertion order and resolution order

        ConfigurationContainer allConfigurations = project.getConfigurations();
        for (Configuration configuration : allConfigurations) {
            DependencySet allDependencies = configuration.getAllDependencies();
            for (Dependency dep : allDependencies) {
                if (dependencyIsAlreadyAdded(dep, boms)) {
                    break;
                }

                Configuration bomConf = project.getConfigurations().detachedConfiguration(
                        project.getDependencies()
                                .create(dep.getGroup() + ":" + dep.getName() + ":" + dep.getVersion() + "@pom"));

                Set<File> files = bomConf.getResolvedConfiguration().getLenientConfiguration().getFiles(Specs.SATISFIES_ALL);
                for (File file : files) {
                    if (shouldAddToBoms(file)) {
                        boms.add(file);
                    }
                }
            }
        }
        return boms;
    }

    boolean shouldAddToBoms(File file) {
        boolean isAPom = file.getName().endsWith(".pom");
        boolean packagingIsPom;

        String fileContents = "";
        try {
            fileContents = new String(Files.readAllBytes(Paths.get(file.toURI())));
        } catch (IOException e) {
            log.info("Problem when parsing file %s", file.toString(), e);
        }
        packagingIsPom = fileContents.toLowerCase().contains("<packaging>pom</packaging>");

        return isAPom && packagingIsPom;
    }

    boolean dependencyIsAlreadyAdded(Dependency dep, Set<File> boms) {
        for (File bom : boms) {
            String dependencyAsFilePath = dep.getGroup() + File.separator + dep.getName() + File.separator + dep.getVersion();
            if (bom.toString().contains(dependencyAsFilePath)) {
                return true;
            }
        }
        return false;
    }
}
