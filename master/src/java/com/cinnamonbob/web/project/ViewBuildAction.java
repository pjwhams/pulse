package com.cinnamonbob.web.project;

import com.cinnamonbob.core.model.Changelist;
import com.cinnamonbob.core.model.CommandResult;
import com.cinnamonbob.core.model.Feature;
import com.cinnamonbob.core.model.StoredArtifact;
import com.cinnamonbob.model.BuildResult;
import com.cinnamonbob.model.Project;
import com.cinnamonbob.model.RecipeResultNode;

import java.util.Iterator;
import java.util.List;

/**
 * 
 *
 */
public class ViewBuildAction extends ProjectActionSupport
{
    private long id;
    private long buildId;
    private Project project;
    private BuildResult result;
    private List<Changelist> changes;

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public long getBuildId()
    {
        return buildId;
    }

    public void setBuildId(long id)
    {
        this.buildId = id;
    }

    public Project getProject()
    {
        return project;
    }

    public BuildResult getResult()
    {
        return result;
    }

    public void validate()
    {

    }

    public String execute()
    {
        project = getProjectManager().getProject(id);
        result = getBuildManager().getBuildResult(buildId);

        for (RecipeResultNode node : result.getRoot().getChildren())
        {
            for (CommandResult r : node.getResult().getCommandResults())
            {
                for (StoredArtifact a : r.getArtifacts())
                {
                    Iterator<Feature.Level> i = a.getLevels();
                    while (i.hasNext())
                    {
                        a.getFeatures(i.next()).size();
                    }
                }
            }
        }

        //changes = model.getChangelists();

        return SUCCESS;
    }

    public List<Changelist> getChanges()
    {
        return changes;
    }

    public void setChanges(List<Changelist> changes)
    {
        this.changes = changes;
    }
}
