package com.zutubi.pulse.core.commands.ant;

import com.zutubi.pulse.core.commands.core.NamedArgumentCommand;
import com.zutubi.pulse.core.commands.core.NamedArgumentCommandConfiguration;
import com.zutubi.tove.annotations.Form;
import com.zutubi.tove.annotations.SymbolicName;
import com.zutubi.util.SystemUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 */
@SymbolicName("zutubi.antCommandConfig")
@Form(fieldOrder = {"name", "workingDir", "buildFile", "targets", "args", "inputFile"})
public class AntCommandConfiguration extends NamedArgumentCommandConfiguration
{
    private String buildFile;
    private String targets;

    public AntCommandConfiguration()
    {
        super(NamedArgumentCommand.class, "ant.bin", SystemUtils.IS_WINDOWS ? "ant.bat" : "ant");
        getPostProcessors().add(new AntPostProcessorConfiguration("ant.pp"));
    }

    public String getBuildFile()
    {
        return buildFile;
    }

    public void setBuildFile(String buildFile)
    {
        this.buildFile = buildFile;
    }

    public String getTargets()
    {
        return targets;
    }

    public void setTargets(String targets)
    {
        this.targets = targets;
    }

    protected List<NamedArgument> getNamedArguments()
    {
        List<NamedArgument> args = new LinkedList<NamedArgument>();
        if (buildFile != null)
        {
            args.add(new NamedArgument("build file", buildFile, "-f"));
        }

        if (targets != null)
        {
            args.add(new NamedArgument("targets", targets, Arrays.asList(targets.split("\\s+"))));
        }

        return args;
    }
}
