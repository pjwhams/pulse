package com.zutubi.pulse.core.commands.api;

import com.zutubi.pulse.core.engine.api.BuildException;
import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.pulse.core.plugins.PostProcessorExtensionManager;
import com.zutubi.pulse.core.postprocessors.api.PostProcessorConfiguration;
import com.zutubi.util.TextUtils;
import com.zutubi.util.io.ForkOutputStream;
import com.zutubi.util.io.IOUtils;
import com.zutubi.util.io.IgnoreCloseOutputStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 */
public abstract class OutputProducingCommandSupport extends CommandSupport
{
    public static final String OUTPUT_NAME = "command output";
    public static final String OUTPUT_FILE = "output.txt";

    private PostProcessorExtensionManager postProcessorExtensionManager;

    protected OutputProducingCommandSupport(OutputProducingCommandConfigurationSupport config)
    {
        super(config);
    }

    @Override
    public OutputProducingCommandConfigurationSupport getConfig()
    {
        return (OutputProducingCommandConfigurationSupport) super.getConfig();
    }

    protected List<Class<? extends PostProcessorConfiguration>> getDefaultPostProcessorTypes()
    {
        return Collections.emptyList();
    }

    protected List<PostProcessorConfiguration> getDefaultPostProcessors(CommandContext commandContext)
    {
        List<PostProcessorConfiguration> result = new LinkedList<PostProcessorConfiguration>();
        ExecutionContext executionContext = commandContext.getExecutionContext();
        for (Class<? extends PostProcessorConfiguration> type: getDefaultPostProcessorTypes())
        {
            String name = postProcessorExtensionManager.getDefaultProcessorName(type);
            if (name != null)
            {
                PostProcessorConfiguration processor = executionContext.getValue(name, PostProcessorConfiguration.class);
                if (processor != null)
                {
                    result.add(processor);
                }
            }
        }

        return result;
    }
    
    public void execute(CommandContext commandContext)
    {
        File outputDir = commandContext.registerOutput(OUTPUT_NAME, null);
        File outputArtifact = new File(outputDir, OUTPUT_FILE);

        OutputStream stream = null;
        try
        {
            stream = getOutputStream(commandContext.getExecutionContext(), outputArtifact);
            execute(commandContext, stream);
        }
        catch (FileNotFoundException e)
        {
            throw new BuildException(e);
        }
        finally
        {
            IOUtils.close(stream);

            // Even on error, process whatever we have captured.
            commandContext.registerProcessors(OUTPUT_NAME, getDefaultPostProcessors(commandContext));
            commandContext.registerProcessors(OUTPUT_NAME, getConfig().getPostProcessors());
        }
    }

    private OutputStream getOutputStream(ExecutionContext context, File outputArtifact) throws FileNotFoundException
    {
        List<OutputStream> outputs = new ArrayList<OutputStream>(3);
        outputs.add(new FileOutputStream(outputArtifact));

        if (context.getOutputStream() != null)
        {
            // Wrap in an ignore close stream as we don't own this stream and
            // thus don't want to close it with the rest when done.
            outputs.add(new IgnoreCloseOutputStream(context.getOutputStream()));
        }

        String outputFile = getConfig().getOutputFile();
        if (TextUtils.stringSet(outputFile))
        {
            try
            {
                outputs.add(new FileOutputStream(new File(getWorkingDir(context.getWorkingDir()), outputFile)));
            }
            catch (FileNotFoundException e)
            {
                throw new BuildException("Unable to create output file '" + outputFile + "': " + e.getMessage(), e);
            }
        }

        OutputStream output;
        if (outputs.size() > 1)
        {
            output = new ForkOutputStream(outputs.toArray(new OutputStream[outputs.size()]));
        }
        else
        {
            output = outputs.get(0);
        }
        return output;
    }

    protected File getWorkingDir(File baseDir)
    {
        return baseDir;
    }

    protected abstract void execute(CommandContext commandContext, OutputStream outputStream);

    public void setPostProcessorExtensionManager(PostProcessorExtensionManager postProcessorExtensionManager)
    {
        this.postProcessorExtensionManager = postProcessorExtensionManager;
    }
}