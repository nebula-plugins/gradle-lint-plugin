/**
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.nebula.lint.rule.wrapper;

import com.google.common.base.Throwables;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.testfixtures.internal.TestBuildScopeServices;
import org.gradle.testfixtures.internal.TestGlobalScopeServices;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author Boaz Jan
 *
 * A duplication of Gradle's ProjectBuilderImpl, but instead of using the DefaultGradle - use our custom gradle
 * that allows us to override the internal version. With this capability we can test various version related rules
 * with significantly more control as it comes to test parameters and outcome predictability
 */
public class WrapperProjectBuilder {
    private static ServiceRegistry globalServices;
    private static final AsmBackedClassGenerator CLASS_GENERATOR = new AsmBackedClassGenerator();

    public Project createProject(String name, File inputProjectDir) {
        File projectDir = prepareProjectDir(inputProjectDir);

        final File homeDir = new File(projectDir, "gradleHome");

        StartParameter startParameter = new StartParameter();
        File userHomeDir = new File(projectDir, "userHome");
        startParameter.setGradleUserHomeDir(userHomeDir);

        NativeServices.initialize(userHomeDir);

        ServiceRegistry topLevelRegistry;
        try {
            Constructor<TestBuildScopeServices> constructor = TestBuildScopeServices.class.getConstructor(ServiceRegistry.class, StartParameter.class, File.class);
            topLevelRegistry = constructor.newInstance(getGlobalServices(), startParameter, homeDir);
        } catch (NoSuchMethodException e) {
            topLevelRegistry = new TestBuildScopeServices(getGlobalServices(), homeDir);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        GradleInternal gradle = CLASS_GENERATOR.newInstance(CustomVersionGradle.class, null, startParameter, topLevelRegistry.get(ServiceRegistryFactory.class));

        DefaultProjectDescriptor projectDescriptor = new DefaultProjectDescriptor(null, name, projectDir, new DefaultProjectDescriptorRegistry(),
                topLevelRegistry.get(FileResolver.class));
        ClassLoaderScope baseScope = gradle.getClassLoaderScope();
        ClassLoaderScope rootProjectScope = baseScope.createChild("root-project");
        ProjectInternal project = topLevelRegistry.get(IProjectFactory.class).createProject(projectDescriptor, null, gradle, rootProjectScope, baseScope);

        gradle.setRootProject(project);
        gradle.setDefaultProject(project);

        return project;
    }

    private ServiceRegistry getGlobalServices() {
        if (globalServices == null) {
            globalServices = ServiceRegistryBuilder
                    .builder()
                    .displayName("global services")
                    .parent(LoggingServiceRegistry.newNestedLogging())
                    .parent(NativeServices.getInstance())
                    .provider(new TestGlobalScopeServices())
                    .build();
        }
        return globalServices;
    }

    public File prepareProjectDir(File projectDir) {
        if (projectDir == null) {
            TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();
            projectDir = temporaryFileProvider.createTemporaryDirectory("gradle", "projectDir");
            // TODO deleteOnExit won't clean up non-empty directories (and it leaks memory for long-running processes).
            projectDir.deleteOnExit();
        } else {
            try {
                projectDir = projectDir.getCanonicalFile();
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
        return projectDir;
    }
}
