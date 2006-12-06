package com.zutubi.pulse.vfs.pulse;

import com.zutubi.pulse.model.BuildResult;
import com.zutubi.pulse.model.Project;
import org.apache.commons.vfs.FileName;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.provider.AbstractFileSystem;

import java.io.InputStream;

/**
 * <class comment/>
 */
public class LatestBuildFileObject extends AbstractPulseFileObject implements AddressableFileObject
{
    public LatestBuildFileObject(final FileName name, final AbstractFileSystem fs)
    {
        super(name, fs);
    }

    public AbstractPulseFileObject createFile(final FileName fileName) throws Exception
    {
        return null;
    }

    protected FileType doGetType() throws Exception
    {
        return FileType.IMAGINARY;
    }

    protected String[] doListChildren() throws Exception
    {
        return new String[0];
    }

    protected long doGetContentSize() throws Exception
    {
        return 0;
    }

    protected InputStream doGetInputStream() throws Exception
    {
        return null;
    }

    public String getUrlPath()
    {
        // what is our context.
        try
        {
            BuildResult build;

            ProjectProvider provider = (ProjectProvider) getAncestor(ProjectProvider.class);
            if (provider != null)
            {
                Project project = provider.getProject();

                BuildSpecificationProvider buildSpecProvider = (BuildSpecificationProvider) getAncestor(BuildSpecificationProvider.class);
                if (buildSpecProvider != null)
                {
                    build = buildManager.getLatestBuildResult(buildSpecProvider.getBuildSpecification());   
                }
                else
                {
                    build = buildManager.getLatestBuildResult(project);
                }
            }
            else
            {
                build = buildManager.getLatestBuildResult();
            }

            return "/viewBuild.action?id=" + build.getId();
        }
        catch (FileSystemException e)
        {
            return null;
        }
    }
}
