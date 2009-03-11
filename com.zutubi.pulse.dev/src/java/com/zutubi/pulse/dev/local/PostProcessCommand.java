package com.zutubi.pulse.dev.local;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.command.BootContext;
import com.zutubi.pulse.command.Command;
import com.zutubi.pulse.core.*;
import com.zutubi.pulse.core.api.PulseRuntimeException;
import com.zutubi.pulse.core.cli.HelpCommand;
import static com.zutubi.pulse.core.engine.api.BuildProperties.NAMESPACE_INTERNAL;
import static com.zutubi.pulse.core.engine.api.BuildProperties.PROPERTY_TEST_RESULTS;
import com.zutubi.pulse.core.engine.api.Reference;
import com.zutubi.pulse.core.model.CommandResult;
import com.zutubi.pulse.core.model.PersistentTestSuiteResult;
import com.zutubi.pulse.core.model.StoredFileArtifact;
import com.zutubi.pulse.core.model.TestResultSummary;
import com.zutubi.pulse.core.postprocessors.DefaultPostProcessorContext;
import com.zutubi.pulse.core.postprocessors.api.PostProcessor;
import com.zutubi.pulse.core.postprocessors.api.PostProcessorContext;
import com.zutubi.pulse.core.resources.ResourceDiscoverer;
import com.zutubi.pulse.core.spring.SpringComponentContext;
import com.zutubi.pulse.dev.bootstrap.DevBootstrapManager;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A command to run a post-processor manually over a single file.  Used to
 * test processor definitions without having to run a full build.
 */
@SuppressWarnings({"AccessStaticViaInstance"})
public class PostProcessCommand implements Command
{
    public static void main(String argv[])
    {
        PostProcessCommand command = new PostProcessCommand();
        System.exit(command.execute(argv, System.out, System.err));
    }

    public int execute(BootContext context)
    {
        return execute(context.getCommandArgv(), System.out, System.err);
    }

    public int execute(String[] argv, PrintStream out, PrintStream err)
    {
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("pulse-file")
                .hasArg()
                .create('p'));
        options.addOption(OptionBuilder.withLongOpt("resources-file")
                .hasArg()
                .create('e'));

        CommandLineParser parser = new PosixParser();
        try
        {
            CommandLine commandLine = parser.parse(options, argv, true);
            String[] args = commandLine.getArgs();
            if(args.length < 2)
            {
                HelpCommand helpCommand = new HelpCommand();
                helpCommand.showHelp("process", this);
                return 1;
            }

            String processorName = args[0];
            String inputFile = args[1];
            String pulseFile = "pulse.xml";
            String resourcesFile = null;

            if (commandLine.hasOption('p'))
            {
                pulseFile = commandLine.getOptionValue('p');
            }

            if (commandLine.hasOption('e'))
            {
                resourcesFile = commandLine.getOptionValue('e');
            }

            postProcess(processorName, inputFile, pulseFile, resourcesFile, out);
        }
        catch (Exception e)
        {
            err.println(e.getMessage());
            return 1;
        }

        return 0;
    }

    private void postProcess(String processorName, String inputFile, String pulseFile, String resourcesFile, PrintStream out)
    {
        DevBootstrapManager.startup("com/zutubi/pulse/dev/local/bootstrap/context/applicationContext.xml");

        try
        {
            File in = checkFile(inputFile, "Input");
            PulseFile pf = loadPulseFile(pulseFile, loadResources(resourcesFile));
            Reference reference = pf.getReference(processorName);
            if(reference == null || !(reference instanceof PostProcessor))
            {
                throw new PulseRuntimeException("Post-processor '" + processorName + "' does not exist.");
            }

            Indenter indenter = new Indenter(out, "  ");
            printPrologue(indenter, processorName, inputFile, pulseFile, resourcesFile);

            PostProcessor processor = (PostProcessor) reference;
            StoredFileArtifact artifact = new StoredFileArtifact(in.getAbsolutePath());
            CommandResult result = new CommandResult("dummy");
            PulseExecutionContext executionContext = new PulseExecutionContext();
            PersistentTestSuiteResult testResults = new PersistentTestSuiteResult();
            executionContext.add(NAMESPACE_INTERNAL, new GenericReference<PersistentTestSuiteResult>(PROPERTY_TEST_RESULTS, testResults));

            PostProcessorContext context = new DefaultPostProcessorContext(artifact, result, executionContext);

            indenter.println("Running processor...");
            processor.process(in, context);
            indenter.println("Post-processing complete.");

            printResults(indenter, artifact, testResults);
        }
        finally
        {
            DevBootstrapManager.shutdown();
        }
    }

    private void printPrologue(Indenter indenter, String processorName, String inputFile, String pulseFile, String resourcesFile)
    {
        indenter.println("pulse file    : '" + pulseFile + "'");
        if (resourcesFile != null)
        {
            indenter.println("resources file  : '" + resourcesFile + "'");
        }

        indenter.println("post-processor: '" + processorName + "'");
        indenter.println("input file    : '" + inputFile + "'");
        indenter.println();
    }

    private void printResults(Indenter indenter, StoredFileArtifact artifact, PersistentTestSuiteResult testResults)
    {
        indenter.println("Features:");
        indenter.indent();
        if(artifact.hasFeatures())
        {
            int count = artifact.getFeatures().size();
            indenter.println(String.format("Found %d feature%s:", count, count > 1 ? "s" : ""));
            PrintSupport.showFeatures(indenter, artifact);
        }
        else
        {
            indenter.println("No features found.");
        }
        indenter.dedent();

        indenter.println("Tests:");
        indenter.indent();
        TestResultSummary summary = testResults.getSummary();
        if(summary.hasTests())
        {
            indenter.println(String.format("Found %d test%s (%s):", summary.getTotal(), summary.getTotal() > 1 ? "s" : "", PrintSupport.summariseTestCounts(summary)));
            indenter.indent();
            PrintSupport.showTestSuite(indenter, testResults);
            indenter.dedent();
        }
        else
        {
            indenter.println("No tests found.");
        }
        indenter.dedent();
    }

    private ResourceRepository loadResources(String resourcesFile)
    {
        FileResourceRepository resourceRepository;
        if(TextUtils.stringSet(resourcesFile))
        {
            File f = checkFile(resourcesFile, "Resources");
            try
            {
                resourceRepository = ResourceFileLoader.load(f);
            }
            catch (Exception e)
            {
                throw new PulseRuntimeException(e.getMessage(), e);
            }
        }
        else
        {
            resourceRepository = new FileResourceRepository();
        }

        ResourceDiscoverer discoverer = new ResourceDiscoverer();
        discoverer.discoverAndAdd(resourceRepository);
        return resourceRepository;
    }

    private PulseFile loadPulseFile(String fileName, ResourceRepository resourceRepository)
    {
        PulseFileLoaderFactory fileLoaderFactory = SpringComponentContext.getBean("fileLoaderFactory");

        File f = checkFile(fileName, "Pulse");
        PulseFileLoader loader = fileLoaderFactory.createLoader();
        PulseFile result = new PulseFile();
        try
        {
            loader.load(new FileInputStream(f), result, new PulseScope(), new LocalFileResolver(f.getParentFile()), resourceRepository, null);
            return result;
        }
        catch (Exception e)
        {
            throw new PulseRuntimeException(e.getMessage(), e);
        }
    }

    private File checkFile(String fileName, String title)
    {
        File f = new File(fileName);
        if(!f.exists())
        {
            throw new PulseRuntimeException(title + " file '" + fileName + "' does not exist");
        }

        if(!f.isFile())
        {
            throw new PulseRuntimeException(title + " file '" + fileName + "' is not a regular file");
        }

        if(!f.canRead())
        {
            throw new PulseRuntimeException(title + " file '" + fileName + "' is not readable");
        }
        return f;
    }

    public String getHelp()
    {
        return "post-process a file";
    }

    public String getDetailedHelp()
    {
        return "Processes a file using a post-processor defined in a local pulse file\n" +
               "(pulse.xml by default).  This is useful for testing the definition of a post-\n" +
               "processor in your development environment.";
    }

    public List<String> getUsages()
    {
        return Arrays.asList("<processor name> <input file>");
    }

    public List<String> getAliases()
    {
        return Arrays.asList("post-process", "pp");
    }

    public Map<String, String> getOptions()
    {
        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("-p [--pulse-file] file",     "use specified pulse file [default: pulse.xml]");
        options.put("-o [--output-dir] dir",      "write output to directory [default: pulse.out]");
        options.put("-e [--resources-file] file", "use specified resources file [default: <none>]");
        return options;
    }

    public boolean isDefault()
    {
        return false;
    }
}