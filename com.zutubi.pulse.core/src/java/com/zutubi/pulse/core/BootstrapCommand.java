package com.zutubi.pulse.core;

import com.zutubi.pulse.core.commands.api.CommandContext;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * An adaptation between the command and Bootstrap interfaces that allows
 * any bootstrapper to be run like a command.  This further allows the result
 * of bootstrapping to be stored as part of the recipe result.
 *
 */
public class BootstrapCommand implements Command
{
    public static final String OUTPUT_NAME = "bootstrap output";
    public static final String FILES_FILE = "files.txt";
    private BootstrapCommandConfiguration config;

    public BootstrapCommand(BootstrapCommandConfiguration config)
    {
        this.config = config;
    }

    public void execute(CommandContext commandContext)
    {
        config.getBootstrapper().bootstrap((PulseExecutionContext) commandContext.getExecutionContext());
    }

    public List<Artifact> getArtifacts()
    {
        PrecapturedArtifact artifact = new PrecapturedArtifact();
        artifact.setName(OUTPUT_NAME);
        
        List<Artifact> artifacts = new LinkedList<Artifact>();
        artifacts.add(artifact);
        return artifacts;
    }

    public List<String> getArtifactNames()
    {
        return Arrays.asList(OUTPUT_NAME);
    }

    public String getName()
    {
        return "bootstrap";
    }

    public void setName(String name)
    {
        // Ignored
    }

    public boolean isForce()
    {
        return false;
    }

    public void terminate()
    {
        config.getBootstrapper().terminate();
    }
}
