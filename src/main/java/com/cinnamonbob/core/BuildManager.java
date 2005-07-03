package com.cinnamonbob.core;

import com.cinnamonbob.bootstrap.StartupManager;
import com.cinnamonbob.bootstrap.ComponentContext;
import com.cinnamonbob.util.FileSystemUtils;
import com.thoughtworks.xstream.XStream;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 
 *
 */
public class BuildManager
{
    private static final Logger LOG = Logger.getLogger(BuildManager.class.getName());

    /**
     *
     */
    private static final String BUILD_ROOT = "builds";

    /**
     *
     */
    private static final String WORK_ROOT = "work";

    private static final String RESULT_FILE_NAME = "result.xml";

    /**
     * Used for (de)serialisation.
     */
    private XStream xstream = new XStream();

    private File projectRoot;


    /**
     * Initialise this manager.
     */
    public BuildManager()
    {
    }

    public void setAllProjectRoot(File f)
    {
        System.out.println("BuildManager.setAllProjectRoot("+f+")");
        this.projectRoot = f;
    }
    
    public File getAllProjectRoot()
    {
        return this.projectRoot;
    }
    
    public static BuildManager getInstance()
    {
        return (BuildManager) ComponentContext.getBean("buildManager");        
    }
    
    public File getProjectRoot(Project project)
    {
        return new File(projectRoot, project.getName());        
    }    

    public File getBuildRoot(Project project)
    {
        return new File(getProjectRoot(project), BUILD_ROOT);
    }

    
    public File getWorkRoot(Project project)
    {
        return new File(getProjectRoot(project), WORK_ROOT);
    }

    
    public int determineNextAvailableBuildId(Project project)
    {
        File buildsDir = getBuildRoot(project);

        if(buildsDir.isDirectory())
        {
            String files[] = buildsDir.list();
            int max = -1;

            for(int i = 0; i < files.length; i++)
            {
                try
                {
                    int buildNumber = Integer.parseInt(files[i]);

                    if(buildNumber > max)
                    {
                        max = buildNumber;
                    }
                }
                catch (NumberFormatException e)
                {
                    // Oh well, not a build dir
                }
            }

            return max + 1;
        }
        else
        {
            return 0;
        }
    }

    /**
     * Execute the default build for the specified project.
     * @param project
     * @param id
     * @return
     */ 
    public void executeBuild(Project project, BuildResult result)
    {
        // Allocate the result with a unique id.
        File buildDir;

        try
        {
            cleanWorkDir(project);
            buildDir = createBuildDir(getBuildRoot(project), result);
        }
        catch(InternalBuildFailureException e)
        {
            // Not even able to create the build directory: bad news.
            result.setInternalFailure(e);
            logInternalBuildFailure(result);
            return;
        }

        // May record internal failures too.
        executeCommands(project, result, buildDir);
        result.finalise();

        try
        {
            saveBuildResult(buildDir, result);
        }
        catch(InternalBuildFailureException e)
        {
            // We basically can't save anything about this, so bail out.
            // Don't clobber earlier failure...
            if(result.getInternalFailure() == null)
            {
                result.setInternalFailure(e);
                logInternalBuildFailure(result);
            }
        }
    }

    private File createBuildDir(File outputDir, BuildResult buildResult) throws InternalBuildFailureException
    {
        File buildDir = getBuildDir(outputDir, buildResult.getId());

        if(!buildDir.mkdirs())
        {
            throw new InternalBuildFailureException("Could not create build directory '" + buildDir.getAbsolutePath() + "'");
        }

        return buildDir;
    }

    private File getBuildDir(File outputDir, int buildId)
	{
        String dirName = String.format("%08d", new Integer(buildId));
        return new File(outputDir, dirName);
	}


    private void logInternalBuildFailure(BuildResult result)
    {
        InternalBuildFailureException e = result.getInternalFailure();

        LOG.severe("Project '" + result.getProjectName() + "' build " + Integer.toString(result.getId()) + ": Internal build failure:");
        LOG.severe(e.getMessage());

        if (e.getCause() != null)
        {
            LOG.severe("Cause: " + e.getCause().getMessage());
        }
    }

    private void cleanWorkDir(Project project) throws InternalBuildFailureException
    {
        File workDir = getWorkRoot(project);
        
        if(workDir.exists())
        {
            if(!FileSystemUtils.removeDirectory(workDir))
            {
                throw new InternalBuildFailureException("Could not clean work directory '" + workDir.getAbsolutePath() + '"');
            }
        }

        if(!workDir.mkdirs())
        {
            throw new InternalBuildFailureException("Could not create work directory '" + workDir.getAbsolutePath() + "'");
        }
    }

    private void executeCommands(Project project, BuildResult result, File buildDir)
    {
        List<BuildResult> history       = project.getHistory(1);
        BuildResult       previousBuild = null;
        
        if(history.size() > 0)
        {
            previousBuild = history.get(0);
        }
        
        try
        {
            int i = 0;
            boolean failed = false;

            for(CommandCommon command: project.getRecipe())
            {
                if(!failed || command.getForce())
                {
                    result.commandCommenced(command.getName());
                    
                    File                commandOutputDir = createCommandOutputDir(buildDir, command, i);
                    CommandResultCommon commandResult    = command.execute(commandOutputDir, previousBuild);

                    result.commandCompleted(commandResult);
                    saveCommandResult(commandOutputDir, commandResult);
                    i++;

                    if(!commandResult.getResult().succeeded())
                    {
                        failed = true;
                    }
                }
            }
        }
        catch(InternalBuildFailureException e)
        {
            result.setInternalFailure(e);
            logInternalBuildFailure(result);
        }
    }

    private void saveBuildResult(File buildDir, BuildResult result) throws InternalBuildFailureException
    {
        File resultFile = new File(buildDir, RESULT_FILE_NAME);

        try
        {
            xstream.toXML(result, new FileWriter(resultFile));
        }
        catch(IOException e)
        {
            throw new InternalBuildFailureException("Could not save build result to file '" + resultFile.getAbsolutePath() + "'", e);
        }
    }


    private void saveCommandResult(File commandOutputDir, CommandResultCommon commandResult) throws InternalBuildFailureException
    {
        File resultFile = new File(commandOutputDir, RESULT_FILE_NAME);

        try
        {
            xstream.toXML(commandResult, new FileWriter(resultFile));
        }
        catch(IOException e)
        {
            throw new InternalBuildFailureException("Could not save command result to file '" + resultFile.getAbsolutePath() + "'", e);
        }
    }

    private File createCommandOutputDir(File buildDir, CommandCommon command, int index) throws InternalBuildFailureException
    {
        String dirName        = String.format("%08d-%s", index, command.getName());
        File commandOutputDir = new File(buildDir, dirName);

        if(!commandOutputDir.mkdir())
        {
            throw new InternalBuildFailureException("Could not create command output directory '" + commandOutputDir.getAbsolutePath() + "'");
        }

        return commandOutputDir;
    }

    private void loadCommandResults(File buildDir, BuildResult result)
    {
        if(buildDir.isDirectory())
        {
            String files[] = buildDir.list();
            Arrays.sort(files);

            for(String dirName: files)
            {
                File dir = new File(buildDir, dirName);

                if(dir.isDirectory())
                {
                    File resultFile = new File(dir, "result.xml");

                    try
                    {
                        CommandResultCommon commandResult = (CommandResultCommon)xstream.fromXML(new FileReader(resultFile));
                        result.commandCompleted(commandResult);
                    }
                    catch(FileNotFoundException e)
                    {
                        LOG.warning("I/O error loading command result from file '" + resultFile.getAbsolutePath() + "': " + e.getMessage());
                    }
                }
            }
        }
    }

	/**
	 * Retrieves a history of recent builds of this project.  The history may
	 * be shorter than requested (even empty) if there have not been enough
	 * previous builds.
	 *
	 * @param maxBuilds
	 *        the maximum number of results to return
     * @param latestBuild
     *        id of the most recent build
	 * @return a list of recent build results, most recent first
	 */
	public List<BuildResult> getHistory(Project project, int latestBuild, int maxBuilds)
	{
		List<BuildResult> history = new LinkedList<BuildResult>();

		for(int i = latestBuild; i >= 0 && history.size() < maxBuilds; i--)
		{
			BuildResult result = loadBuild(project, i);
			if(result != null)
			{
				history.add(result);
			}
		}

		return history;
	}

    private BuildResult loadBuild(Project project, int buildId)
    {
        File        buildDir   = getBuildDir(getBuildRoot(project), buildId);
        File        resultFile = new File(buildDir, RESULT_FILE_NAME);
        BuildResult result     = null;

        try
        {
            result = (BuildResult)xstream.fromXML(new FileReader(resultFile));
            result.load(project.getName(), buildId, buildDir);
            loadCommandResults(buildDir, result);
        }
        catch(IOException e)
        {
            LOG.warning("I/O error loading build result from file '" + resultFile.getAbsolutePath() + "'");
        }

        return result;
    }

    public BuildResult getBuildResult(Project project, int id)
    {
        return loadBuild(project, id);
    }
}
