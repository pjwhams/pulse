package com.zutubi.pulse.web.project;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.core.model.Change;
import com.zutubi.pulse.core.model.Changelist;
import com.zutubi.pulse.core.scm.config.ScmConfiguration;
import com.zutubi.pulse.model.BuildManager;
import com.zutubi.pulse.model.BuildResult;
import com.zutubi.pulse.model.ChangelistUtils;
import com.zutubi.pulse.model.Project;
import com.zutubi.pulse.model.persistence.ChangelistDao;
import com.zutubi.pulse.prototype.config.project.ProjectConfiguration;
import com.zutubi.pulse.prototype.config.project.changeviewer.ChangeViewerConfiguration;
import com.zutubi.pulse.web.ActionSupport;
import com.zutubi.util.Sort;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 */
public class ViewChangelistAction extends ActionSupport
{
    private long id;
    private Changelist changelist;
    private ChangelistDao changelistDao;
    private BuildManager buildManager;

    /** If we drilled down from the project, this is the project ID */
    private String projectName;
    private Project project;

    /** This is the build result we have drilled down from, if any. */
    private String buildVID;
    private BuildResult buildResult;

    /** All builds affected by this change. */
    private List<BuildResult> buildResults;

    private boolean changeViewerInitialised;
    private ChangeViewerConfiguration changeViewer;
    private ScmConfiguration scm;

    private String fileViewUrl;
    private String fileDownloadUrl;
    private String fileDiffUrl;

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public String getu_projectName()
    {
        return uriComponentEncode(projectName);
    }

    public String geth_projectName()
    {
        return htmlEncode(projectName);
    }

    public Project getProject()
    {
        return project;
    }

    public String getBuildVID()
    {
        return buildVID;
    }

    public void setBuildVID(String buildVID)
    {
        this.buildVID = buildVID;
    }

    public BuildResult getBuildResult()
    {
        return buildResult;
    }

    public Changelist getChangelist()
    {
        return changelist;
    }

    public ChangeViewerConfiguration getChangeViewer()
    {
        if(!changeViewerInitialised)
        {
            changeViewerInitialised = true;
            
            Project p = project;
            if(buildResult != null)
            {
                p = buildResult.getProject();
            }

            if(p != null)
            {
                ProjectConfiguration projectConfig = projectManager.getProjectConfig(p.getId());
                changeViewer = projectConfig.getChangeViewer();
                scm = projectConfig.getScm();
            }
            else
            {
                for(long id: changelist.getProjectIds())
                {
                    p = projectManager.getProject(id);
                    ProjectConfiguration projectConfig = projectManager.getProjectConfig(id);
                    if(p != null && projectConfig != null && projectConfig.getChangeViewer() != null)
                    {
                        changeViewer = projectConfig.getChangeViewer();
                        scm = projectConfig.getScm();
                        break;
                    }
                }
            }
        }

        return changeViewer;
    }

    public boolean haveChangeViewer()
    {
        return getChangeViewer() != null;
    }
    
    public String getChangeUrl()
    {
        ChangeViewerConfiguration changeViewer = getChangeViewer();
        if(changeViewer != null && changeViewer.hasCapability(ChangeViewerConfiguration.Capability.VIEW_CHANGESET))
        {
            return changeViewer.getChangesetURL(changelist.getRevision());
        }

        return null;
    }

    public String getFileViewUrl()
    {
        return fileViewUrl;
    }

    public String getFileDownloadUrl()
    {
        return fileDownloadUrl;
    }

    public String getFileDiffUrl()
    {
        return fileDiffUrl;
    }

    public void updateUrls(Change change)
    {
        updateFileViewUrl(change);
        updateFileDownloadUrl(change);
        updateFileDiffUrl(change);
    }

    public void updateFileViewUrl(Change change)
    {
        ChangeViewerConfiguration changeViewer = getChangeViewer();
        if(changeViewer != null && changeViewer.hasCapability(ChangeViewerConfiguration.Capability.VIEW_FILE))
        {
            fileViewUrl = changeViewer.getFileViewURL(change.getFilename(), change.getRevisionString());
        }
        else
        {
            fileViewUrl = null;
        }
    }

    public void updateFileDownloadUrl(Change change)
    {
        ChangeViewerConfiguration changeViewer = getChangeViewer();
        if(changeViewer != null && changeViewer.hasCapability(ChangeViewerConfiguration.Capability.DOWNLOAD_FILE))
        {
            fileDownloadUrl = changeViewer.getFileDownloadURL(change.getFilename(), change.getRevisionString());
        }
        else
        {
            fileDownloadUrl = null;
        }
    }

    public void updateFileDiffUrl(Change change)
    {
        if(diffableAction(change.getAction()))
        {
            String previous = scm.getPreviousRevision(change.getRevisionString());
            if(previous != null)
            {
                ChangeViewerConfiguration changeViewer = getChangeViewer();
                if(changeViewer != null && changeViewer.hasCapability(ChangeViewerConfiguration.Capability.VIEW_FILE_DIFF))
                {
                    fileDiffUrl = changeViewer.getFileDiffURL(change.getFilename(), change.getRevisionString());
                    return;
                }
            }
        }

        fileDiffUrl = null;
    }

    private boolean diffableAction(Change.Action action)
    {
        switch(action)
        {
            case EDIT:
            case INTEGRATE:
            case MERGE:
                return true;
            default:
                return false;
        }
    }

    public void setChangelist(Changelist changelist)
    {
        this.changelist = changelist;
    }

    public String execute()
    {
        changelist = changelistDao.findById(id);
        if (changelist == null)
        {
            addActionError("Unknown changelist '" + id + "'");
            return ERROR;
        }

        if(TextUtils.stringSet(projectName))
        {
            project = projectManager.getProject(projectName);
        }
        if(TextUtils.stringSet(buildVID))
        {
            // It is valid to have no build ID set: we may not be viewing
            // the change as part of a build.
            buildResult = buildManager.getByProjectAndVirtualId(project, buildVID);
        }

        buildResults = ChangelistUtils.getBuilds(buildManager, changelist);
        Collections.sort(buildResults, new Comparator<BuildResult>()
        {
            public int compare(BuildResult b1, BuildResult b2)
            {
                Sort.StringComparator comparator = new Sort.StringComparator();
                int result = comparator.compare(b1.getProject().getName(), b2.getProject().getName());
                if(result == 0)
                {
                    result = (int)(b1.getNumber() - b2.getNumber());
                }

                return result;
            }
        });

        return SUCCESS;
    }

    public void setChangelistDao(ChangelistDao changelistDao)
    {
        this.changelistDao = changelistDao;
    }

    public void setBuildManager(BuildManager buildManager)
    {
        this.buildManager = buildManager;
    }

    public List<BuildResult> getBuildResults()
    {
        return buildResults;
    }
}
