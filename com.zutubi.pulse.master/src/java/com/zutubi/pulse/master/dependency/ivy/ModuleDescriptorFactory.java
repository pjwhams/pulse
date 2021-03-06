/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.master.dependency.ivy;

import com.google.common.base.Function;
import com.zutubi.i18n.Messages;
import com.zutubi.pulse.core.dependency.ivy.IvyConfiguration;
import com.zutubi.pulse.core.dependency.ivy.IvyModuleDescriptor;
import com.zutubi.pulse.core.model.CommandResult;
import com.zutubi.pulse.core.model.RecipeResult;
import com.zutubi.pulse.core.model.StoredArtifact;
import com.zutubi.pulse.core.model.StoredFileArtifact;
import com.zutubi.pulse.master.bootstrap.MasterConfigurationManager;
import com.zutubi.pulse.master.model.BuildResult;
import com.zutubi.pulse.master.model.RecipeResultNode;
import com.zutubi.pulse.master.tove.config.project.BuildStageConfiguration;
import com.zutubi.pulse.master.tove.config.project.DependenciesConfiguration;
import com.zutubi.pulse.master.tove.config.project.DependencyConfiguration;
import com.zutubi.pulse.master.tove.config.project.ProjectConfiguration;
import com.zutubi.tove.config.api.Configurations;
import com.zutubi.util.UnaryProcedure;
import org.apache.ivy.core.module.id.ModuleRevisionId;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Collections2.transform;

/**
 * This factory creates an ivy module descriptor from the pulse data and configuration.
 */
public class ModuleDescriptorFactory
{
    private static final Messages I18N = Messages.getInstance(ModuleDescriptorFactory.class);
    
    private static final Function<BuildStageConfiguration, String> STAGE_NAME_FUNCTION = Configurations.toConfigurationName();
    
    private final IvyConfiguration configuration;
    private final MasterConfigurationManager configurationManager;

    public ModuleDescriptorFactory(IvyConfiguration configuration, MasterConfigurationManager configurationManager)
    {
        this.configuration = configuration;
        this.configurationManager = configurationManager;
    }

    /**
     * Create a module descriptor that contains the details necessary to retrieve the
     * dependencies as defined by the configuration.
     *
     * @param project the configuration on which to base the descriptor
     * @return the created descriptor.
     * @see #createRetrieveDescriptor(ProjectConfiguration, BuildResult, String)
     */
    public IvyModuleDescriptor createRetrieveDescriptor(ProjectConfiguration project)
    {
        return createRetrieveDescriptor(project, null, null);
    }

    /**
     * Create a module descriptor that contains the details necessary to retrieve the
     * dependencies as defined by the configuration.
     * <p/>
     * When the specified build result indicates that one of the projects dependencies
     * was rebuilt as part of the build, then the version of the dependency produced by
     * the recent build is used if possible.  This ensures that consistent artifacts
     * are used across an extended build.
     *
     * @param project  the configuration on which to base the descriptor
     * @param result   the build result for the current build
     * @param revision the revision of the descriptor
     * @return the created descriptor.
     */
    public IvyModuleDescriptor createRetrieveDescriptor(ProjectConfiguration project, BuildResult result, String revision)
    {
        IvyModuleDescriptor ivyDescriptor = newDescriptor(project, result, revision);
        addDependencies(project, result, ivyDescriptor);
        return ivyDescriptor;
    }

    private void addDependencies(ProjectConfiguration project, BuildResult result, IvyModuleDescriptor ivyDescriptor)
    {
        DependenciesConfiguration dependencies = project.getDependencies();
        for (DependencyConfiguration dependency : dependencies.getDependencies())
        {
            ModuleRevisionId dependencyMrid = getDependencyMRID(result, dependency);
            if (dependency.getStageType() == DependencyConfiguration.StageType.CORRESPONDING_STAGES)
            {
                ivyDescriptor.addOptionalDependency(dependencyMrid.getName());
            }
            
            for (BuildStageConfiguration stage: project.getStages().values())
            {
                List<String> stageNames = new LinkedList<String>();
                switch (dependency.getStageType())
                {
                    case ALL_STAGES:
                        stageNames.add(IvyModuleDescriptor.ALL_STAGES);
                        break;
                    case CORRESPONDING_STAGES:
                        stageNames.add(stage.getName());
                        break;
                    case SELECTED_STAGES:
                        stageNames.addAll(transform(dependency.getStages(), STAGE_NAME_FUNCTION));
                        break;
                }

                ivyDescriptor.addDependency(dependencyMrid, stage.getName(), dependency.isTransitive(), stageNames.toArray(new String[stageNames.size()]));
            }
        }
    }

    private IvyModuleDescriptor newDescriptor(ProjectConfiguration project, BuildResult result, String revision)
    {
        String status = result != null && result.getStatus() != null ? result.getStatus() : project.getDependencies().getStatus();
        return new IvyModuleDescriptor(MasterIvyModuleRevisionId.newInstance(project, revision), status, configuration);
    }

    /**
     * Determine the module revision that this dependency references.  If the build result is part of a
     * larger meta build that also built the dependency, then the dependency will reference the specific
     * dependency revision that was produced as part of the meta build.  Otherwise, the dependency will
     * reference the revision as defined by the configuration.
     *
     * @param result     a build result, or null if this processing occurs outside the context of a build.
     * @param dependency the dependency configuration
     * @return the module revision we should be depending on.
     */
    private ModuleRevisionId getDependencyMRID(BuildResult result, DependencyConfiguration dependency)
    {
        ProjectConfiguration dependsOnProject = dependency.getProject();
        BuildResult dependsOnResult = result != null ? result.getDependsOn(dependsOnProject.getName()) : null;
        ModuleRevisionId dependencyMrid;
        if (dependsOnResult != null)
        {
            dependencyMrid = MasterIvyModuleRevisionId.newInstance(dependsOnProject, dependsOnResult.getVersion());
        }
        else
        {
            dependencyMrid = MasterIvyModuleRevisionId.newInstance(dependency);
        }
        return dependencyMrid;
    }

    /**
     * Adds information about the artifacts to be published by the given build result to the give descriptor.
     *
     * @param result        the build that generated the artifacts to published
     * @param ivyDescriptor descriptor to add the artifacts to
     */
    public void addArtifacts(BuildResult result, IvyModuleDescriptor ivyDescriptor)
    {
        final Collection<ArtifactDetail> artifacts = new LinkedList<ArtifactDetail>();

        result.forEachNode(new UnaryProcedure<RecipeResultNode>()
        {
            public void run(RecipeResultNode node)
            {
                if (node.getStageHandle() == 0) // skip the root.
                {
                    return;
                }

                RecipeResult recipeResult = node.getResult();
                for (CommandResult commandResult : recipeResult.getCommandResults())
                {
                    for (StoredArtifact storedArtifact : commandResult.getArtifacts())
                    {
                        if (storedArtifact.isPublish())
                        {
                            for (StoredFileArtifact storedFileArtifact : storedArtifact.getChildren())
                            {
                                artifacts.add(new ArtifactDetail(node, recipeResult, commandResult, storedArtifact, storedFileArtifact, configurationManager.getDataDirectory()));
                            }
                        }
                    }
                }
            }
        });

        boolean successful = true;
        for (ArtifactDetail artifact : artifacts)
        {
            if (!addArtifact(artifact, ivyDescriptor))
            {
                successful = false;
            }
        }
        if (!successful)
        {
            result.warning(I18N.format("artifact.failure"));
        }
    }

    private boolean addArtifact(ArtifactDetail artifactDetail, IvyModuleDescriptor descriptor)
    {
        RecipeResult result = artifactDetail.getRecipeResult();
        String artifactFilename = artifactDetail.getArtifactFile().getName();

        Matcher m = artifactDetail.getPattern().matcher(artifactFilename);
        if (m.matches())
        {
            String artifactName = IvyModuleDescriptor.UNKNOWN;
            if (m.groupCount() > 0)
            {
                artifactName = m.group(1);
            }
            String artifactExt = null;
            if (m.groupCount() > 1)
            {
                artifactExt = m.group(2);
            }

            if (artifactExt == null)
            {
                artifactExt = IvyModuleDescriptor.UNKNOWN;
            }

            if (artifactName == null || artifactName.trim().length() == 0)
            {
                result.warning(I18N.format("pattern.match.missingArtifactName", artifactDetail.getArtifactPattern(), artifactFilename));
                return false;
            }

            descriptor.addArtifact(artifactName, artifactExt, artifactExt, artifactDetail.getArtifactFile(), artifactDetail.getStageName());
            return true;
        }
        else
        {
            result.warning(I18N.format("pattern.match.failed", artifactDetail.getArtifactPattern(), artifactFilename));
            return false;
        }
    }

    /**
     * A value class used to hold context details about an artifact.  This artifact detail
     * represents a single stored artifact file.
     */
    private static class ArtifactDetail
    {
        private final RecipeResultNode node;
        private final RecipeResult recipeResult;
        private final CommandResult commandResult;
        private final StoredArtifact storedArtifact;
        private final StoredFileArtifact storedArtifactFile;
        private final File dataDir;

        private ArtifactDetail(RecipeResultNode node, RecipeResult recipeResult, CommandResult commandResult, StoredArtifact storedArtifact, StoredFileArtifact storedArtifactFile, File dataDir)
        {
            this.node = node;
            this.recipeResult = recipeResult;
            this.commandResult = commandResult;
            this.storedArtifact = storedArtifact;
            this.storedArtifactFile = storedArtifactFile;
            this.dataDir = dataDir;
        }

        public File getArtifactFile()
        {
            File outputDir = commandResult.getAbsoluteOutputDir(dataDir);
            return new File(outputDir, storedArtifactFile.getPath());
        }

        public Pattern getPattern()
        {
            return Pattern.compile(storedArtifact.getArtifactPattern());
        }

        public String getArtifactPattern()
        {
            return storedArtifact.getArtifactPattern();
        }

        public String getStageName()
        {
            return node.getStageName();
        }

        public RecipeResult getRecipeResult()
        {
            return recipeResult;
        }
    }
}
