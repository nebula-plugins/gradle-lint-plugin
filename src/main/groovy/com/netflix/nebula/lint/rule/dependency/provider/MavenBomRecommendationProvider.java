/*
 *
 *  Copyright 2018 Netflix, Inc.
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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuilder;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.apache.maven.model.path.DefaultPathTranslator;
import org.apache.maven.model.path.DefaultUrlNormalizer;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.ValueSource;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MavenBomRecommendationProvider extends ClasspathBasedRecommendationProvider {
    private final Logger log = LoggerFactory.getLogger(MavenBomRecommendationProvider.class);
    private Map<String, String> recommendations;

    public MavenBomRecommendationProvider(Project project) {
        super(project);
    }

    private static class SimpleModelSource implements ModelSource2 {
        InputStream in;

        public SimpleModelSource(InputStream in) {
            this.in = in;
        }

        @Override
        public InputStream getInputStream() throws java.io.IOException {
            return in;
        }

        @Override
        public String getLocation() {
            return null;
        }

        @Override
        public ModelSource2 getRelatedSource(String relPath) {
            return null;
        }

        @Override
        public java.net.URI getLocationURI() {
            return null;
        }
    }

    @Override
    public String getVersion(String org, String name) throws Exception {
        return getRecommendations().get(org + ":" + name);
    }

    private Map<String, String> getRecommendations() throws Exception {
        if (recommendations == null) {
            recommendations = new HashMap<>();

            Set<File> recommendationFiles = getBomsOnConfiguration();
            for (File recommendation : recommendationFiles) {
                if (!recommendation.getName().endsWith("pom")) {
                    break;
                }

                DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();

                request.setModelResolver(new ModelResolver() {
                    @Override
                    public ModelSource2 resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
                        String notation = groupId + ":" + artifactId + ":" + version + "@pom";
                        org.gradle.api.artifacts.Dependency dependency = project.getDependencies().create(notation);
                        org.gradle.api.artifacts.Configuration configuration = project.getConfigurations().detachedConfiguration(dependency);
                        try {
                            File file = configuration.getFiles().iterator().next();
                            return new SimpleModelSource(new java.io.FileInputStream(file));
                        } catch (Exception e) {
                            throw new UnresolvableModelException(e, groupId, artifactId, version);
                        }
                    }

                    @Override
                    public ModelSource2 resolveModel(Dependency dependency) throws UnresolvableModelException {
                        return resolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
                    }

                    @Override
                    public ModelSource2 resolveModel(Parent parent) throws UnresolvableModelException {
                        return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
                    }

                    @Override
                    public void addRepository(Repository repository) throws InvalidRepositoryException {
                        // do nothing
                    }

                    @Override
                    public void addRepository(Repository repository, boolean bool) throws InvalidRepositoryException {
                        // do nothing
                    }

                    @Override
                    public ModelResolver newCopy() {
                        return this; // do nothing
                    }
                });
                request.setModelSource(new SimpleModelSource(new java.io.FileInputStream(recommendation)));
                request.setSystemProperties(System.getProperties());

                DefaultModelBuilder modelBuilder = new DefaultModelBuilderFactory().newInstance();
                modelBuilder.setModelInterpolator(new ProjectPropertiesModelInterpolator(project));

                ModelBuildingResult result = modelBuilder.build(request);
                log.info("using bom: " + result.getEffectiveModel().getId());
                Model model = result.getEffectiveModel();
                if (model == null) {
                    break;
                }
                org.apache.maven.model.DependencyManagement dependencyManagement = model.getDependencyManagement();
                if (dependencyManagement == null) {
                    break;
                }
                for (Dependency d : dependencyManagement.getDependencies()) {
                    recommendations.put(d.getGroupId() + ":" + d.getArtifactId(), d.getVersion()); // overwrites previous values
                }
            }
        }
        return recommendations;
    }

    private static class ProjectPropertiesModelInterpolator extends StringSearchModelInterpolator {
        private final Project project;

        ProjectPropertiesModelInterpolator(Project project) {
            this.project = project;
            setUrlNormalizer(new DefaultUrlNormalizer());
            setPathTranslator(new DefaultPathTranslator());
        }

        public java.util.List<ValueSource> createValueSources(Model model, File projectDir, ModelBuildingRequest request, ModelProblemCollector collector) {
            java.util.List<ValueSource> sources = new ArrayList<>();
            sources.addAll(super.createValueSources(model, projectDir, request, collector));
            sources.add(new PropertiesBasedValueSource(System.getProperties()));
            sources.add(new MapBasedValueSource(project.getProperties()));
            return sources;
        }
    }
}
