package com.zutubi.pulse.core.commands.ant;

import com.zutubi.pulse.core.ProcessArtifact;
import com.zutubi.pulse.core.commands.core.ExecutableCommand;
import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.pulse.core.model.CommandResult;
import com.zutubi.util.SystemUtils;

/**
 */
public class AntCommand extends ExecutableCommand
{
    private String buildFile;
    private String targets;

    public AntCommand()
    {
        super("ant.bin", SystemUtils.IS_WINDOWS ? "ant.bat" : "ant");
    }

    public void execute(ExecutionContext context, CommandResult cmdResult)
    {
        if (buildFile != null)
        {
            addArguments("-f", buildFile);
            cmdResult.getProperties().put("build file", buildFile);
        }

        if (targets != null)
        {
            addArguments(targets.split(" +"));
            cmdResult.getProperties().put("targets", targets);
        }

        ProcessArtifact pa = createProcess();
        pa.setProcessor(new AntPostProcessor("ant.pp"));

        super.execute(context, cmdResult);
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
}