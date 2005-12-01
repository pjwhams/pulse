package com.cinnamonbob.model;

import com.cinnamonbob.core.model.Result;
import com.cinnamonbob.core.model.ResultState;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 */
public class BuildResult extends Result
{
    private Project project;
    private long number;
    /**
     * Map from SCM id to details for that SCM.
     */
    private Map<Long, BuildScmDetails> scmDetails;
    private List<RecipeResultNode> results;

    public BuildResult()
    {

    }

    public BuildResult(Project project, long number)
    {
        this.project = project;
        this.number = number;
        state = ResultState.INITIAL;
        results = new LinkedList<RecipeResultNode>();
    }

    public Project getProject()
    {
        return project;
    }

    private void setProject(Project project)
    {
        this.project = project;
    }

    public long getNumber()
    {
        return number;
    }

    private void setNumber(long number)
    {
        this.number = number;
    }

    public Map<Long, BuildScmDetails> getScmDetails()
    {
        if (scmDetails == null)
        {
            scmDetails = new HashMap<Long, BuildScmDetails>();
        }
        return scmDetails;
    }

    public BuildScmDetails getScmDetails(long scmId)
    {
        return scmDetails.get(scmId);
    }

    private void setScmDetails(Map<Long, BuildScmDetails> scmDetails)
    {
        this.scmDetails = scmDetails;
    }

    public void addScmDetails(long scmId, BuildScmDetails details)
    {
        getScmDetails().put(scmId, details);
    }

    public boolean hasChanges()
    {
        for (BuildScmDetails details : scmDetails.values())
        {
            if (details.getRevision() != null || details.getChangelists().size() > 0)
            {
                return true;
            }
        }

        return false;
    }

    public List<RecipeResultNode> getResults()
    {
        return results;
    }

    private void setResults(List<RecipeResultNode> results)
    {
        this.results = results;
    }

    public void add(RecipeResultNode result)
    {
        results.add(result);
    }
}
