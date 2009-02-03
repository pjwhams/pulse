package com.zutubi.pulse.core.commands.xcode;

import com.zutubi.pulse.core.commands.core.NamedArgumentCommand;
import com.zutubi.pulse.core.commands.core.NamedArgumentCommandConfiguration;
import com.zutubi.tove.annotations.SymbolicName;
import com.zutubi.util.StringUtils;
import com.zutubi.util.TextUtils;

import java.util.LinkedList;
import java.util.List;

/**
 */
@SymbolicName("zutubi.xcodeCommandConfig")
public class XCodeCommandConfiguration extends NamedArgumentCommandConfiguration
{
    private String target;
    private String config;
    private String project;

    private String buildaction;
    private List<String> settings;

    public XCodeCommandConfiguration()
    {
        super(NamedArgumentCommand.class, "xcode.bin", "xcodebuild");
        getPostProcessors().add(new XCodePostProcessorConfiguration("xcode.pp"));
    }

    protected List<NamedArgument> getNamedArguments()
    {
        List<NamedArgument> result = new LinkedList<NamedArgument>();
        if (TextUtils.stringSet(project))
        {
            result.add(new NamedArgument("project", project, "-project"));
        }

        if (TextUtils.stringSet(config))
        {
            result.add(new NamedArgument("configuration", config, "-configuration"));
        }

        if (TextUtils.stringSet(target))
        {
            result.add(new NamedArgument("target", target, "-target"));
        }

        if (TextUtils.stringSet(buildaction))
        {
            result.add(new NamedArgument("build action", buildaction));
        }

        if (settings != null && settings.size() > 0)
        {
            result.add(new NamedArgument("settings", StringUtils.unsplit(settings), settings));
        }

        return result;
    }

    public String getTarget()
    {
        return target;
    }

    public void setTarget(String target)
    {
        this.target = target;
    }

    public String getConfig()
    {
        return config;
    }

    public void setConfig(String config)
    {
        this.config = config;
    }

    public String getProject()
    {
        return project;
    }

    public void setProject(String project)
    {
        this.project = project;
    }

    public String getBuildaction()
    {
        return buildaction;
    }

    public void setBuildaction(String buildaction)
    {
        this.buildaction = buildaction;
    }

    public List<String> getSettings()
    {
        return settings;
    }

    public void setSettings(List<String> settings)
    {
        this.settings = settings;
    }
}