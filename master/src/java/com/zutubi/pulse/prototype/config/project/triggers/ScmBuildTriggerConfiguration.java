package com.zutubi.pulse.prototype.config.project.triggers;

import com.zutubi.config.annotations.SymbolicName;
import com.zutubi.prototype.config.ConfigurationProvider;
import com.zutubi.pulse.prototype.config.project.ProjectConfiguration;
import com.zutubi.pulse.scheduling.EventTrigger;
import com.zutubi.pulse.scheduling.ScmChangeEventFilter;
import com.zutubi.pulse.scheduling.Trigger;
import com.zutubi.pulse.scheduling.tasks.BuildProjectTask;
import com.zutubi.pulse.scm.ScmChangeEvent;

/**
 * A trigger that fires when a code change is detected in the project's SCM.
 */
@SymbolicName("zutubi.scmTriggerConfig")
public class ScmBuildTriggerConfiguration extends TriggerConfiguration
{
    private ConfigurationProvider configurationProvider;

    public Trigger newTrigger()
    {
        ProjectConfiguration project = configurationProvider.getAncestorOfType(this, ProjectConfiguration.class);
        Trigger trigger = new EventTrigger(ScmChangeEvent.class, getTriggerName(), getTriggerGroup(project), ScmChangeEventFilter.class);
        trigger.setTaskClass(BuildProjectTask.class);
        trigger.setProject(project.getProjectId());
        
        return trigger;
    }

    public String getType()
    {
        return "scm";
    }

    public void setConfigurationProvider(ConfigurationProvider configurationProvider)
    {
        this.configurationProvider = configurationProvider;
    }
}
