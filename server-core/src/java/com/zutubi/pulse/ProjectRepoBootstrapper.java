package com.zutubi.pulse;

import com.zutubi.pulse.core.*;
import com.zutubi.pulse.model.Scm;
import com.zutubi.pulse.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;

/**
 * The Project Repo Bootstrapper checks out a project into a the project-root/project-name/repo
 * directory and then runs an update when necessary, copying the results into the build directory.
 */
public class ProjectRepoBootstrapper implements Bootstrapper
{
    private final String projectName;
    private final String specName;
    private final Scm scm;
    private BuildRevision revision;
    private String agent;

    public ProjectRepoBootstrapper(String projectName, String specName, Scm scm, BuildRevision revision)
    {
        this.projectName = projectName;
        this.specName = specName;
        this.scm = scm;
        this.revision = revision;
    }

    public void bootstrap(final CommandContext context) throws BuildException
    {
        final RecipePaths paths = context.getPaths();
        if (paths.getPersistentWorkDir() == null)
        {
            throw new BuildException("Attempt to use update bootstrapping when no persistent working directory is available.");
        }

        File reposDir = new File(paths.getPersistentWorkDir(), "repos");
        final File localDir = new File(reposDir, FileSystemUtils.composeFilename(projectName, specName));

        // run the scm bootstrapper on the local directory,
        ScmBootstrapper bootstrapper = selectBootstrapper(localDir);
        bootstrapper.prepare(agent);
        
        bootstrapper.bootstrap(new CommandContext(new RecipePaths()
        {
            public File getPersistentWorkDir()
            {
                return paths.getPersistentWorkDir();
            }

            public File getBaseDir()
            {
                return localDir;
            }

            public File getOutputDir()
            {
                return null;
            }
        }, context.getOutputDir()));

        // copy these details to the base directory.
        File baseDir = paths.getBaseDir();
        if (baseDir.exists() && !baseDir.delete())
        {
            throw new BuildException("Unable to bootstrap " + baseDir);
        }
        try
        {
            FileSystemUtils.copyRecursively(localDir, baseDir);
        }
        catch (IOException e)
        {
            throw new BuildException(e);
        }
    }

    public void prepare(String agent)
    {
        this.agent = agent;
    }

    private ScmBootstrapper selectBootstrapper(final File localDir)
    {
        if (!localDir.exists() && !localDir.mkdirs())
        {
            throw new BuildException("Failed to initialise local scm directory: " + localDir.getAbsolutePath());
        }

        // else we can update.
        if (localDir.list().length == 0)
        {
            return new CheckoutBootstrapper(projectName, specName, scm, revision, true);
        }
        else
        {
            return new UpdateBootstrapper(projectName, specName, scm, revision);
        }
    }
}
