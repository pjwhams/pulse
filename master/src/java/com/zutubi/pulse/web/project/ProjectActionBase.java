package com.zutubi.pulse.web.project;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.core.model.Feature;
import com.zutubi.pulse.model.BuildManager;
import com.zutubi.pulse.model.BuildResult;
import com.zutubi.pulse.model.Project;
import com.zutubi.pulse.model.User;
import com.zutubi.pulse.web.ActionSupport;
import com.zutubi.pulse.web.LookupErrorException;
import org.acegisecurity.AccessDeniedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 */
public class ProjectActionBase extends ActionSupport
{
    private String projectName;
    private Project project;
    private long projectId;
    protected BuildManager buildManager;

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getu_projectName()
    {
        return uriComponentEncode(projectName);
    }

    public String geth_projectName()
    {
        return htmlEncode(projectName);
    }
    
    public long getProjectId()
    {
        return projectId;
    }

    public Feature.Level getErrorLevel()
    {
        return Feature.Level.ERROR;
    }

    public Feature.Level getWarningLevel()
    {
        return Feature.Level.WARNING;
    }

    public List<Feature.Level> getFeatureLevels()
    {
        List<Feature.Level> list = Arrays.asList(Feature.Level.values());
        Collections.reverse(list);
        return list;
    }
    
    public Project getProject()
    {
        if(project == null)
        {
            if (TextUtils.stringSet(projectName))
            {
                project = projectManager.getProject(projectName);
                if(project == null)
                {
                    throw new LookupErrorException("Unknown project '" + projectName + "'");
                }
                projectId = project.getId();
            }
        }

        return project;
    }

    public Project getRequiredProject()
    {
        Project project = getProject();
        if(project == null)
        {
            throw new LookupErrorException("Project name is required");
        }

        return project;
    }

    public void checkPermissions(BuildResult result)
    {
        if(result.isPersonal())
        {
            User user = getLoggedInUser();
            if(!result.getUser().equals(user))
            {
                throw new AccessDeniedException("Only the owner can view a personal build");
            }
        }
    }

    public void setBuildManager(BuildManager buildManager)
    {
        this.buildManager = buildManager;
    }
}
