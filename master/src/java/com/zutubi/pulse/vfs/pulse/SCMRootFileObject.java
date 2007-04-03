package com.zutubi.pulse.vfs.pulse;

import com.opensymphony.xwork.ActionContext;
import com.zutubi.pulse.model.Scm;
import com.zutubi.pulse.web.project.AddProjectWizard;
import org.apache.commons.vfs.FileName;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileType;
import org.apache.commons.vfs.provider.AbstractFileSystem;

import java.util.Map;

/**
 * Used to browse a Pulse project's SCM.  This file object represents the
 * root of the SCM view.
 */
public class SCMRootFileObject extends AbstractPulseFileObject implements SCMProvider
{
    public SCMRootFileObject(final FileName name, final AbstractFileSystem fs)
    {
        super(name, fs);
    }

    public AbstractPulseFileObject createFile(final FileName fileName) throws Exception
    {
        return objectFactory.buildBean(SCMFileObject.class,
                                       new Class[]{FileName.class, AbstractFileSystem.class},
                                       new Object[]{fileName, pfs});
    }

    protected FileType doGetType() throws Exception
    {
        return FileType.FOLDER;
    }

    protected String[] doListChildren() throws Exception
    {
        return new String[]{"scm"};
    }

    public Scm getScm() throws FileSystemException
    {
        ProjectProvider projectProvider = getAncestor(ProjectProvider.class);
        if(projectProvider.getProjectId() == 0)
        {
            // Assume project setup
            Map session = ActionContext.getContext().getSession();
            if (!session.containsKey(AddProjectWizard.class.getName()))
            {
                throw new FileSystemException("Unable to locate SCM configuration from previous wizard step");
            }

            AddProjectWizard wizard = (AddProjectWizard) session.get(AddProjectWizard.class.getName());
            return wizard.getScm();            
        }
        else
        {
            return projectProvider.getProject().getScm();
        }
    }
}
