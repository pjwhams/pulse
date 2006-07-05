package com.zutubi.pulse.web.project;

import com.zutubi.pulse.core.BuildRevision;
import com.zutubi.pulse.model.BuildSpecification;
import com.zutubi.pulse.model.ManualTriggerBuildReason;
import com.zutubi.pulse.model.Project;

import java.util.List;

public class TriggerBuildAction extends ProjectActionSupport
{
    private long id;

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public void validate()
    {
    }

    public String execute()
    {
        Project project = getProjectManager().getProject(projectId);

        if (project == null)
        {
            addActionError("Trigger build request for unknown project ID '" + projectId + "'");
            return ERROR;
        }

        getProjectManager().checkWrite(project);

        List<BuildSpecification> specs = project.getBuildSpecifications();
        BuildSpecification spec = null;

        for (BuildSpecification s : specs)
        {
            if (s.getId() == id)
            {
                spec = s;
                break;
            }
        }

        if (spec == null)
        {
            addActionError("Request to build unknown build specification id '" + id + "' for project '" + project.getName() + "'");
            return ERROR;
        }

        getProjectManager().triggerBuild(project, spec.getName(), new ManualTriggerBuildReason((String)getPrinciple()), new BuildRevision(), true);

        try
        {
            // Pause for dramatic effect
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
        }

        return SUCCESS;
    }
}
