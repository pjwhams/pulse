package com.zutubi.pulse.acceptance.pages.agents;

import com.zutubi.pulse.acceptance.SeleniumBrowser;
import com.zutubi.pulse.acceptance.pages.SeleniumPage;
import com.zutubi.pulse.master.webwork.Urls;
import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Mapping;
import com.zutubi.util.Predicate;
import com.zutubi.util.Sort;

import java.util.Collections;
import java.util.List;

/**
 * The front page of the "agents" section, listing all agents.
 */
public class AgentsPage extends SeleniumPage
{
    private static final String ID_AGENTS_TABLE = "agents-table";
    private static final String EXECUTING_NONE = "none";

    public AgentsPage(SeleniumBrowser browser, Urls urls)
    {
        super(browser, urls, ID_AGENTS_TABLE, "agents");
    }

    public String getUrl()
    {
        return urls.agents();
    }

    public String getStatusId(String agent)
    {
        return "agent." + agent + ".status";
    }

    public String getStatus(String agent)
    {
        return browser.getText(getStatusId(agent));
    }

    public String getActionId(String agent, String action)
    {
        return "agent." + agent + ".action." + action;
    }

    public List<String> getActions(final String agent)
    {
        String[] allLinks = browser.getAllLinks();
        List<String> actions = CollectionUtils.filter(allLinks, new Predicate<String>()
        {
            public boolean satisfied(String s)
            {
                return s.startsWith("agent." + agent + ".action.");
            }
        });

        actions = CollectionUtils.map(actions, new Mapping<String, String>()
        {
            public String map(String s)
            {
                return s.split("\\.")[3];
            }
        });

        Collections.sort(actions, new Sort.StringComparator());
        return actions;
    }

    public boolean isActionAvailable(String agent, String action)
    {
        return browser.isElementIdPresent(getActionId(agent, action));
    }

    public void clickAction(String agent, String action)
    {
        browser.click(getActionId(agent, action));
        browser.waitForPageToLoad();
    }

    /**
     * Indicates if there is info about an executing build on the given agent.
     * 
     * @param agentName name of the agent to test
     * @return true if there is a build executing on the agent, false otherwise
     */
    public boolean isExecutingBuildPresent(String agentName)
    {
        String details = getExecutingBuildDetails(agentName);
        return !EXECUTING_NONE.equals(details);
    }

    /**
     * Returns details of an executing build on the given agent.
     * 
     * @param agentName name of the agent to get the info for
     * @return text of the executing build stage cell for the agent
     */
    public String getExecutingBuildDetails(String agentName)
    {
        return browser.getText("agent." + agentName + ".build");
    }
}