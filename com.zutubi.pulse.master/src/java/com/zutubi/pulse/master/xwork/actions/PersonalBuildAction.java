package com.zutubi.pulse.master.xwork.actions;

import com.opensymphony.webwork.ServletActionContext;
import com.opensymphony.webwork.dispatcher.multipart.MultiPartRequestWrapper;
import com.opensymphony.xwork.ActionContext;
import com.zutubi.pulse.core.scm.api.Revision;
import com.zutubi.pulse.core.scm.api.WorkingCopy;
import com.zutubi.pulse.master.MasterBuildPaths;
import com.zutubi.pulse.master.bootstrap.MasterConfigurationManager;
import com.zutubi.pulse.master.model.BuildManager;
import com.zutubi.pulse.master.model.BuildResult;
import com.zutubi.pulse.master.model.Project;
import com.zutubi.pulse.master.model.User;
import com.zutubi.pulse.master.tove.config.group.ServerPermission;
import com.zutubi.util.io.IOUtils;
import com.zutubi.util.logging.Logger;
import org.acegisecurity.AccessDeniedException;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Action for requesting a personal build.  The personal build client posts to
 * this action, with both simple parameters and a file upload (the patch).
 */
public class PersonalBuildAction extends ActionSupport
{
    private static final Logger LOG = Logger.getLogger(PersonalBuildAction.class);

    private String project;
    private String revision;
    private long number = 0;
    private List<String> responseErrors = new LinkedList<String>();
    private List<String> responseWarnings = new LinkedList<String>();
    private MasterConfigurationManager configurationManager;
    private BuildManager buildManager;

    public void setProject(String project)
    {
        this.project = project;
    }

    public void setRevision(String revision)
    {
        this.revision = revision;
    }

    public long getNumber()
    {
        return number;
    }

    public List<String> getResponseErrors()
    {
        return responseErrors;
    }

    public List<String> getResponseWarnings()
    {
        return responseWarnings;
    }

    public String execute()
    {
        ActionContext ac = ActionContext.getContext();
        HttpServletRequest request = (HttpServletRequest) ac.get(ServletActionContext.HTTP_REQUEST);

        User user = null;
        Object principle = getPrinciple();
        if(principle != null)
        {
            user = userManager.getUser((String) principle);
        }

        if(user == null)
        {
            responseErrors.add("Unable to determine user");
            return ERROR;
        }

        if(!accessManager.hasPermission(userManager.getPrinciple(user), ServerPermission.PERSONAL_BUILD.toString(), null))
        {
            throw new AccessDeniedException("User does not have authority to submit personal build requests.");
        }

        if (!(request instanceof MultiPartRequestWrapper))
        {
            responseErrors.add("Invalid request: expecting multipart POST");
            return ERROR;
        }

        MultiPartRequestWrapper mpr = (MultiPartRequestWrapper) request;
        File[] files = mpr.getFiles("patch.zip");
        if(files == null || files.length == 0 || files[0] == null)
        {
            responseErrors.add("POST does not contain required file parameter 'patch.zip'");
            return ERROR;
        }

        File uploadedPatch = files[0];
        if(!uploadedPatch.exists())
        {
            responseErrors.add("Uploaded patch file '" + uploadedPatch.getAbsolutePath() + "' does not exist");
            return ERROR;
        }

        if(!uploadedPatch.isFile())
        {
            responseErrors.add("Uploaded patch file '" + uploadedPatch.getAbsolutePath() + "' is not a regular file");
            return ERROR;
        }

        Project p = projectManager.getProject(project, false);
        if(p == null)
        {
            responseErrors.add("Unknown project '" + project + "'");
            return ERROR;
        }

        number = userManager.getNextBuildNumber(user);
        MasterBuildPaths paths = new MasterBuildPaths(configurationManager);
        File patchDir = paths.getUserPatchDir(user.getId());
        if(!patchDir.isDirectory())
        {
            if (!patchDir.mkdirs())
            {
                responseErrors.add("Unable to create patch directory '" + patchDir.getAbsolutePath() + "'.");
                return ERROR;
            }
        }

        File patchFile = paths.getUserPatchFile(user.getId(), number);
        if(patchFile.exists())
        {
            responseErrors.add("Patch file '" + patchFile.getAbsolutePath() + "' already exists.  Retry the build.");
            return ERROR;
        }

        try
        {
            IOUtils.copyFile(uploadedPatch, patchFile);
            if (!uploadedPatch.delete())
            {
                responseWarnings.add("Unable to clean up uploaded patch.");
            }
            
            projectManager.triggerBuild(number, p, user, convertRevision(p), patchFile);
        }
        catch (Exception e)
        {
            LOG.severe(e);
            responseErrors.add(e.getClass().getName() + ": " + e.getMessage());
            return ERROR;
        }

        return SUCCESS;
    }

    private Revision convertRevision(Project project)
    {
        if (revision == null || revision.equals(WorkingCopy.REVISION_FLOATING.getRevisionString()))
        {
            return null;
        }
        else if (revision.equals(WorkingCopy.REVISION_LAST_KNOWN_GOOD.getRevisionString()))
        {
            BuildResult lastKnownGood = buildManager.getLatestSuccessfulBuildResult(project);
            if (lastKnownGood == null)
            {
                // Let it float.
                responseWarnings.add("No successful build to establish last known good revision.");
                responseWarnings.add("Using floating revision.");
                return null;
            }
            else
            {
                return new Revision(lastKnownGood.getRevision().getRevisionString());
            }
        }
        else
        {
            return new Revision(revision);
        }
    }

    public void setConfigurationManager(MasterConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }

    public void setBuildManager(BuildManager buildManager)
    {
        this.buildManager = buildManager;
    }
}
