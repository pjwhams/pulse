package com.zutubi.pulse.core.commands.api;

import com.zutubi.pulse.core.Command;
import com.zutubi.pulse.core.engine.api.Addable;
import com.zutubi.pulse.core.postprocessors.api.PostProcessorConfiguration;
import com.zutubi.tove.annotations.Reference;
import com.zutubi.tove.annotations.SymbolicName;

import java.util.LinkedList;
import java.util.List;

/**
 */
@SymbolicName("zutubi.outputProducingCommandConfigSupport")
public class OutputProducingCommandConfigurationSupport extends CommandConfigurationSupport
{
    private String outputFile;
    @Reference @Addable(value = "process", reference = "processor")
    private List<PostProcessorConfiguration> postProcessors = new LinkedList<PostProcessorConfiguration>();

    public OutputProducingCommandConfigurationSupport(Class<? extends Command> commandType)
    {
        super(commandType);
    }

    public String getOutputFile()
    {
        return outputFile;
    }

    public void setOutputFile(String outputFile)
    {
        this.outputFile = outputFile;
    }

    public List<PostProcessorConfiguration> getPostProcessors()
    {
        return postProcessors;
    }

    public void setPostProcessors(List<PostProcessorConfiguration> postProcessors)
    {
        this.postProcessors = postProcessors;
    }
}
