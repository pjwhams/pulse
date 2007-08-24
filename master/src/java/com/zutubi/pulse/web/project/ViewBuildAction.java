package com.zutubi.pulse.web.project;

import com.zutubi.pulse.bootstrap.MasterConfigurationManager;
import com.zutubi.pulse.core.model.Changelist;
import com.zutubi.pulse.core.model.TestSuiteResult;
import com.zutubi.pulse.model.BuildColumns;
import com.zutubi.pulse.model.BuildResult;
import com.zutubi.pulse.model.User;
import com.zutubi.pulse.prototype.config.user.UserSettingsConfiguration;
import com.zutubi.util.logging.Logger;

import java.util.List;
import java.util.Stack;

/**
 *
 *
 */
public class ViewBuildAction extends CommandActionBase
{
    public static final String FAILURE_LIMIT_PROPERTY = "pulse.test.failure.limit";
    public static final int DEFAULT_FAILURE_LIMIT = 100;

    private static final Logger LOG = Logger.getLogger(ViewBuildAction.class);

    private List<Changelist> changelists;
    private BuildColumns summaryColumns;

    private MasterConfigurationManager configurationManager;
    /**
     * Insanity to work around lack of locals in velocity.
     */
    private Stack<String> pathStack = new Stack<String>();

    public boolean haveRecipeResultNode()
    {
        return getRecipeResultNode() != null;
    }

    public boolean haveCommandResult()
    {
        return getCommandResult() != null;
    }

    public BuildResult getResult()
    {
        return getBuildResult();
    }

    public BuildColumns getSummaryColumns()
    {
        // Lazy init: not always used.
        if(summaryColumns == null)
        {
            User u = getLoggedInUser();
            summaryColumns = new BuildColumns(u == null ? UserSettingsConfiguration.defaultProjectColumns() : u.getPreferences().getSettings().getProjectSummaryColumns(), projectManager);
        }
        return summaryColumns;
    }

    public static int getFailureLimit()
    {
        int limit = DEFAULT_FAILURE_LIMIT;
        String property = System.getProperty(FAILURE_LIMIT_PROPERTY);
        if(property != null)
        {
            try
            {
                limit = Integer.parseInt(property);
            }
            catch(NumberFormatException e)
            {
                LOG.warning(e);
            }
        }

        return limit;
    }

    public void validate()
    {

    }

    public String execute()
    {
        // Initialise detail down to the command level (optional)
        getCommandResult();

        BuildResult result = getRequiredBuildResult();
        checkPermissions(result);

        result.loadFeatures(configurationManager.getDataDirectory());

        if(result.completed())
        {
            result.loadFailedTestResults(configurationManager.getDataDirectory(), getFailureLimit());
        }

        return SUCCESS;
    }

    public String appendSuitePath(String path, TestSuiteResult suite)
    {
        return path + "/" + urlEncode(suite.getName());
    }

    public String pushSuite(TestSuiteResult suite)
    {
        if(pathStack.empty())
        {
            return pathStack.push(urlEncode(suite.getName()));
        }
        else
        {
            return pathStack.push(appendSuitePath(pathStack.peek(), suite));
        }
    }

    public void popSuite()
    {
        pathStack.pop();
    }

    public List<Changelist> getChangelists()
    {
        if(changelists == null)
        {
            changelists = buildManager.getChangesForBuild(getResult());
        }
        return changelists;
    }

    public void setConfigurationManager(MasterConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }
}
