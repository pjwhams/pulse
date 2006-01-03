package com.cinnamonbob.web.project;

import com.cinnamonbob.model.*;
import com.cinnamonbob.model.persistence.BuildSpecificationNodeDao;
import com.cinnamonbob.model.persistence.SlaveDao;
import com.cinnamonbob.web.ActionSupport;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 */
public class CreateBuildSpecificationNodeAction extends ActionSupport
{
    private long specId;
    private long parentId;
    private BuildStage stage = new BuildStage();
    private BuildSpecificationNodeDao buildSpecificationNodeDao;
    private SlaveDao slaveDao;
    private Long buildHost;
    private Map<Long, String> buildHosts;

    public long getSpecId()
    {
        return specId;
    }

    public void setSpecId(long specId)
    {
        this.specId = specId;
    }

    public long getParentId()
    {
        return parentId;
    }

    public void setParentId(long parentId)
    {
        this.parentId = parentId;
    }

    public BuildStage getStage()
    {
        return stage;
    }

    public void setStage(BuildStage stage)
    {
        this.stage = stage;
    }

    public void setBuildSpecificationNodeDao(BuildSpecificationNodeDao buildSpecificationNodeDao)
    {
        this.buildSpecificationNodeDao = buildSpecificationNodeDao;
    }

    public void setSlaveDao(SlaveDao slaveDao)
    {
        this.slaveDao = slaveDao;
    }

    public Map<Long, String> getBuildHosts()
    {
        return buildHosts;
    }

    public Long getBuildHost()
    {
        return buildHost;
    }

    public void setBuildHost(Long buildHost)
    {
        this.buildHost = buildHost;
    }

    public void validate()
    {
        if (hasErrors())
        {
            // do not attempt to validate unless all other validation rules have
            // completed successfully.
            return;
        }

        if (buildSpecificationNodeDao.findById(parentId) == null)
        {
            addActionError("No build specification node found for id '" + Long.toString(parentId) + "'");
        }

        if (buildHost != 0 && slaveDao.findById(buildHost) == null)
        {
            addActionError("No build host found for id '" + buildHost.toString() + "'");
        }
    }

    public String execute()
    {
        BuildSpecificationNode parent = buildSpecificationNodeDao.findById(parentId);

        if (buildHost == 0)
        {
            stage.setHostRequirements(new MasterBuildHostRequirements());
        }
        else
        {
            Slave slave = slaveDao.findById(buildHost);
            stage.setHostRequirements(new SlaveBuildHostRequirements(slave));
        }

        if (stage.getRecipe() != null && stage.getRecipe().equals(""))
        {
            stage.setRecipe(null);
        }

        BuildSpecificationNode node = new BuildSpecificationNode(stage);
        parent.addChild(node);
        buildSpecificationNodeDao.save(node);

        return SUCCESS;
    }

    public String doDefault()
    {
        List<Slave> slaves = slaveDao.findAll();

        buildHosts = new TreeMap<Long, String>();
        buildHosts.put(0L, "[master]");

        for (Slave slave : slaves)
        {
            buildHosts.put(slave.getId(), slave.getName());
        }

        return SUCCESS;
    }
}
