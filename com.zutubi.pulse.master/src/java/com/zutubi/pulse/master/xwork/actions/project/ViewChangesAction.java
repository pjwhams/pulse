package com.zutubi.pulse.master.xwork.actions.project;

import com.zutubi.pulse.core.model.ChangelistComparator;
import com.zutubi.pulse.core.model.PersistentChangelist;
import com.zutubi.pulse.core.model.ResultState;
import com.zutubi.pulse.master.model.BuildResult;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 */
public class ViewChangesAction extends BuildActionBase
{
    private long sinceBuild = 0;
    private BuildResult previous;
    private BuildResult previousSuccessful;
    private BuildResult previousUnsuccessful;
    private BuildResult sinceResult;
    private List<PersistentChangelist> changelists;

    public long getSinceBuild()
    {
        return sinceBuild;
    }

    public void setSinceBuild(long sinceBuild)
    {
        this.sinceBuild = sinceBuild;
    }

    public BuildResult getSinceResult()
    {
        return sinceResult;
    }

    public BuildResult getPrevious()
    {
        return previous;
    }

    public BuildResult getPreviousSuccessful()
    {
        return previousSuccessful;
    }

    public BuildResult getPreviousUnsuccessful()
    {
        return previousUnsuccessful;
    }

    public List<PersistentChangelist> getChangelists()
    {
        return changelists;
    }

    private BuildResult getPrevious(ResultState[] states)
    {
        BuildResult result = getRequiredBuildResult();
        if(result.getNumber() == 1)
        {
            return null;
        }

        int offset = 0;
        while(true)
        {
            List<BuildResult> previousResults = buildManager.queryBuilds(result.getProject(), states, -1, result.getNumber() - 1, offset, 1, true, false);
            if(previousResults.size() > 0)
            {
                BuildResult buildResult = previousResults.get(0);
                if(!buildResult.isUserRevision() && buildResult.getRevision() != null)
                {
                    return buildResult;
                }
            }
            else
            {
                return null;
            }

            offset++;
        }
    }

    public String execute()
    {
        BuildResult result = getRequiredBuildResult();
        if(result.isPersonal())
        {
            return "personal";
        }

        previous = getPrevious(ResultState.getCompletedStates());
        if(sinceBuild == 0)
        {
            sinceResult = previous;
            if(previous != null)
            {
                sinceBuild = sinceResult.getNumber();
            }
        }
        else
        {
            if(sinceBuild >= result.getNumber())
            {
                addActionError("Invalid build range");
                return ERROR;
            }

            sinceResult = buildManager.getByProjectAndNumber(result.getProject(), sinceBuild);
            if(sinceResult == null)
            {
                addActionError("No such build [" + sinceBuild + "]");
                return ERROR;
            }
        }

        changelists = new LinkedList<PersistentChangelist>();

        // Get changes for all results after since, up to and including to.
        if (sinceBuild != 0)
        {
            List<BuildResult> resultRange = buildManager.queryBuilds(result.getProject(), ResultState.getCompletedStates(), sinceBuild + 1, result.getNumber() - 1, 0, -1, true, false);
            for(BuildResult r: resultRange)
            {
                changelists.addAll(buildManager.getChangesForBuild(r));
            }
        }
        changelists.addAll(buildManager.getChangesForBuild(result));
        Collections.sort(changelists, new ChangelistComparator());
        
        previousSuccessful = getPrevious(new ResultState[] { ResultState.SUCCESS });
        previousUnsuccessful = getPrevious(ResultState.getBrokenStates());

        return SUCCESS;
    }
}
