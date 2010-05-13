package com.zutubi.pulse.acceptance.pages.admin;

import com.zutubi.pulse.acceptance.AcceptanceTestUtils;
import com.zutubi.pulse.acceptance.SeleniumBrowser;
import com.zutubi.pulse.master.webwork.Urls;
import com.zutubi.tove.type.record.PathUtils;
import com.zutubi.util.WebUtils;

import java.util.List;

import static com.zutubi.util.WebUtils.uriPathEncode;
import static java.util.Arrays.asList;

/**
 * A page in the admin UI that displays a single composite.  This page
 * carries a form when the config exists or is configurable and writable.  It
 * also displays state, navigation and action links in various circumstances.
 */
public class CompositePage extends ConfigPage
{
    private static final String CONFIGURE_LINK = "configure";
    private static final String ID_ANCESTOR_NAV = "ancestor-nav";
    private static final String ID_ANCESTOR_COMBO = "ancestor-combo";
    private static final String ID_DESCENDANT_NAV = "descendant-nav";
    private static final String ID_DESCENDANT_COMBO = "descendant-combo";

    private String path;

    public CompositePage(SeleniumBrowser browser, Urls urls, String path)
    {
        super(browser, urls, path);
        this.path = path;
    }

    public String getPath()
    {
        return path;
    }

    public String getUrl()
    {
        return urls.admin() + uriPathEncode(path) + "/";
    }

    public boolean isConfigureLinkPresent()
    {
        return browser.isLinkPresent(CONFIGURE_LINK);
    }

    public boolean isConfiguredDescendantPresent(String descendant)
    {
        return browser.isElementIdPresent(WebUtils.toValidHtmlName("cd-" + descendant));
    }

    public void clickConfigure()
    {
        browser.click(CONFIGURE_LINK);
    }

    public String getActionId(String action)
    {
        return "action." + action;
    }

    public boolean isActionPresent(String action)
    {
        return browser.isElementIdPresent(getActionId(action));
    }

    public void clickAction(String action)
    {
        browser.click(getActionId(action));
    }

    public void clickActionAndWait(String action)
    {
        clickAction(action);
        waitForAction();
    }

    public boolean isDescendantActionsPresent()
    {
        return browser.isElementPresent("descendant.actions");
    }

    public String getDescendantActionId(String action)
    {
        return "descendant.action." + action;
    }

    public boolean isDescendantActionPresent(String action)
    {
        return browser.isElementPresent(getDescendantActionId(action));
    }

    public void clickDescendantAction(String action)
    {
        browser.click(getDescendantActionId(action));
    }

    public void clickDescendantActionAndWait(String action)
    {
        clickDescendantAction(action);
        waitForAction();
    }

    private void waitForAction()
    {
        browser.waitForVariable("actionInProgress", true);
        waitFor();
    }

    public String getErrorsId()
    {
        return "nested-errors";
    }

    public boolean areNestedErrorsPresent()
    {
        return browser.isElementIdPresent(getErrorsId());
    }

    public boolean isLinksBoxPresent()
    {
        return browser.isElementIdPresent("config.links");
    }

    public boolean isLinkPresent(String name)
    {
        return browser.isElementIdPresent(getLinkId(name));
    }

    public void clickLink(String name)
    {
        browser.click(getLinkId(name));
    }

    private String getLinkId(String name)
    {
        return "link." + name;
    }

    public boolean isAncestorNavigationPresent()
    {
        return browser.isElementPresent(ID_ANCESTOR_NAV);
    }

    public List<String> getAncestorNavigationOptions()
    {
        return asList(AcceptanceTestUtils.getComboOptions(browser, ID_ANCESTOR_COMBO));
    }
    
    public CompositePage navigateToAncestorAndWait(String ancestor)
    {
        return navigateAndWait(ID_ANCESTOR_COMBO, ancestor);
    }

    public boolean isDescendantNavigationPresent()
    {
        return browser.isElementPresent(ID_DESCENDANT_NAV);
    }

    public List<String> getDescendantNavigationOptions()
    {
        return asList(AcceptanceTestUtils.getComboOptions(browser, ID_DESCENDANT_COMBO));
    }

    public CompositePage navigateToDescendantAndWait(String ancestor)
    {
        return navigateAndWait(ID_DESCENDANT_COMBO, ancestor);
    }
    
    private CompositePage navigateAndWait(String comboId, String ancestor)
    {
        AcceptanceTestUtils.setComboByValue(browser, comboId, ancestor);
        String[] pathElements = PathUtils.getPathElements(path);
        pathElements[1] = ancestor;
        CompositePage page = new CompositePage(browser, urls, PathUtils.getPath(pathElements));
        page.waitFor();
        return page;
    }
}
