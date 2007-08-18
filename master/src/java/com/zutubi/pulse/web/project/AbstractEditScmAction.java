package com.zutubi.pulse.web.project;

import com.zutubi.pulse.model.BuildSpecification;
import com.zutubi.pulse.model.Project;
import com.zutubi.pulse.model.Scm;
import com.zutubi.pulse.model.SlaveManager;
import com.zutubi.pulse.xwork.interceptor.Preparable;

import java.util.Arrays;
import java.util.List;

/**
 *
 *
 */
public abstract class AbstractEditScmAction extends ProjectActionSupport implements Preparable
{
    private long id;
    private Project project;
    private static final List<String> ID_PARAMS = Arrays.asList("id", "projectId");
    private boolean monitor;
    private SlaveManager slaveManager;

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public boolean isMonitor()
    {
        return monitor;
    }

    public void setMonitor(boolean active)
    {
        this.monitor = active;
    }

    public Project getProject()
    {
        return project;
    }

    public List<String> getPrepareParameterNames()
    {
        return ID_PARAMS;
    }

    public void prepare() throws Exception
    {
        project = getProjectManager().getProject(projectId);
    }

    public String doInput()
    {
        monitor = project.getScm().isMonitor();
        return INPUT;
    }

    public String execute()
    {
        getScm().setMonitor(monitor);
        getScmManager().save(getScm());
        project = getProjectManager().getProject(projectId);
        for(BuildSpecification spec: project.getBuildSpecifications())
        {
            spec.markForCleanBuild(slaveManager.getAll());
            getProjectManager().save(spec);
        }
        return SUCCESS;
    }

    public abstract Scm getScm();

    public abstract String getScmProperty();

    public void setSlaveManager(SlaveManager slaveManager)
    {
        this.slaveManager = slaveManager;
    }
}
