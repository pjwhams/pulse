package com.zutubi.pulse.core.commands.core;

import com.zutubi.events.DefaultEventManager;
import com.zutubi.events.Event;
import com.zutubi.events.EventListener;
import com.zutubi.events.EventManager;
import com.zutubi.pulse.core.Command;
import com.zutubi.pulse.core.PulseExecutionContext;
import com.zutubi.pulse.core.Recipe;
import com.zutubi.pulse.core.SimpleRecipePaths;
import static com.zutubi.pulse.core.engine.api.BuildProperties.NAMESPACE_INTERNAL;
import static com.zutubi.pulse.core.engine.api.BuildProperties.PROPERTY_RECIPE_PATHS;
import com.zutubi.pulse.core.events.CommandCommencedEvent;
import com.zutubi.pulse.core.events.CommandCompletedEvent;
import com.zutubi.pulse.core.model.CommandResult;
import com.zutubi.pulse.core.model.PersistentFeature;
import com.zutubi.pulse.core.model.StoredArtifact;
import com.zutubi.pulse.core.model.StoredFileArtifact;
import com.zutubi.pulse.core.postprocessors.api.Feature;
import com.zutubi.pulse.core.test.api.PulseTestCase;
import com.zutubi.util.FileSystemUtils;
import com.zutubi.util.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class CommandTestBase extends PulseTestCase implements EventListener
{
    protected File baseDir;
    protected File outputDir;

    protected EventManager eventManager;
    protected LinkedBlockingQueue<Event> events;

    public CommandTestBase()
    {
    }

    public CommandTestBase(String name)
    {
        super(name);
    }

    public void setUp() throws IOException
    {
        baseDir = FileSystemUtils.createTempDir(getClass().getName(), ".base");
        outputDir = FileSystemUtils.createTempDir(getClass().getName(), ".out");

        eventManager = new DefaultEventManager();
        events = new LinkedBlockingQueue<Event>(10);
        eventManager.register(this);
    }

    public void tearDown() throws IOException
    {
        removeDirectory(baseDir);
        removeDirectory(outputDir);
    }

    protected CommandResult runCommand(Command command) throws Exception
    {
        return runCommand(command, new PulseExecutionContext());
    }

    /**
     * Simple framework for method for running a command within the context of a recipe.
     *
     * @param command to be executed.
     * @param context for the commands execution, if one exists.
     * @return the command result instance generated by the execution of this command.
     */
    protected CommandResult runCommand(Command command, PulseExecutionContext context)
    {
        context.addValue(NAMESPACE_INTERNAL, PROPERTY_RECIPE_PATHS, new SimpleRecipePaths(baseDir, outputDir));
        context.setWorkingDir(baseDir);

        Recipe recipe = new Recipe();
        recipe.setEventManager(eventManager);
        recipe.add(command, null);
        recipe.execute(context);

        assertCommandCommenced(command.getName());
        CommandCompletedEvent event = assertCommandCompleted(command.getName());

        return event.getResult();
    }

    protected void checkContents(File outputFile, String... contents) throws IOException
    {
        checkContents(outputFile, true, contents);
    }

    protected void checkArtifact(CommandResult result, StoredArtifact artifact, String... contents) throws IOException
    {
        assertNotNull(artifact);
        File expectedFile = getCommandArtifact(result, artifact);
        checkContents(expectedFile, true, contents);
    }

    protected void checkContents(File outputFile, boolean caseSensitive, String... contents) throws IOException
    {
        FileInputStream is = null;
        try
        {
            is = new FileInputStream(outputFile);
            String output = IOUtils.inputStreamToString(is);
            assertOutputContains(output, caseSensitive, contents);
        }
        finally
        {
            IOUtils.close(is);
        }
    }

    protected void assertOutputContains(String output, String... contents)
    {
        assertOutputContains(output, true, contents);
    }

    protected void assertOutputContains(String output, boolean caseSensitive, String... contents)
    {
        if (!caseSensitive)
        {
            output = output.toLowerCase();
        }
        for (String content : contents)
        {
            if (!caseSensitive)
            {
                content = content.toLowerCase();
            }
            if (!output.contains(content))
            {
                fail("Output '" + output + "' does not contain '" + content + "'");
            }
        }
    }

    /**
     * Helper method retrieving the next command published via the event manager.
     *
     * @return next event.
     */
    private Event assertEvent()
    {
        Event e = null;

        try
        {
            e = events.poll(30, TimeUnit.SECONDS);
        }
        catch (InterruptedException e1)
        {
            e1.printStackTrace();
            fail();
        }

        assertNotNull(e);
        return e;
    }

    private void assertCommandCommenced(String name)
    {
        Event e = assertEvent();
        assertTrue(e instanceof CommandCommencedEvent);

        CommandCommencedEvent ce = (CommandCommencedEvent) e;
        assertEquals(name, ce.getName());
    }

    private CommandCompletedEvent assertCommandCompleted(String commandName)
    {
        Event e = assertEvent();
        assertTrue(e instanceof CommandCompletedEvent);

        CommandCompletedEvent ce = (CommandCompletedEvent) e;
        assertEquals(commandName, ce.getResult().getCommandName());
        return ce;
    }

    protected File getCommandArtifact(CommandResult result, StoredFileArtifact fileArtifact)
    {
        String commandDirName = String.format("00000000-%s", result.getCommandName());
        return new File(outputDir, FileSystemUtils.composeFilename(commandDirName, fileArtifact.getPath()));
    }

    protected File getCommandArtifact(CommandResult result, StoredArtifact artifact)
    {
        String commandDirName = String.format("00000000-%s", result.getCommandName());
        return new File(outputDir, FileSystemUtils.composeFilename(commandDirName, artifact.getFile().getPath()));
    }

    protected StoredFileArtifact getOutputArtifact(CommandResult result)
    {
        return result.getFileArtifact(ExecutableCommand.OUTPUT_ARTIFACT_NAME + "/" + ExecutableCommand.OUTPUT_FILENAME);
    }

    protected void assertErrorsMatch(StoredFileArtifact artifact, String... summaryRegexes)
    {
        assertFeatures(artifact, Feature.Level.ERROR, summaryRegexes);
    }

    protected void assertWarningsMatch(StoredFileArtifact artifact, String... summaryRegexes)
    {
        assertFeatures(artifact, Feature.Level.WARNING, summaryRegexes);
    }

    protected void assertFeatures(StoredFileArtifact artifact, Feature.Level level, String... summaryRegexes)
    {
        List<PersistentFeature> features = artifact.getFeatures(level);
        assertEquals(summaryRegexes.length, features.size());
        for(int i = 0; i < summaryRegexes.length; i++)
        {
            String summary = features.get(i).getSummary();
            assertTrue("Summary '" + summary + "' does not match regex '" + summaryRegexes[i], summary.matches(summaryRegexes[i]));
        }
    }

    public void handleEvent(Event evt)
    {
        events.add(evt);
    }

    public Class[] getHandledEvents()
    {
        return new Class[]{Event.class};
    }
}