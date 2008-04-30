package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.forms.SignupForm;
import com.zutubi.pulse.acceptance.pages.LoginPage;
import com.zutubi.pulse.acceptance.pages.WelcomePage;
import com.zutubi.pulse.acceptance.pages.admin.ProjectHierarchyPage;
import com.zutubi.pulse.acceptance.pages.browse.ProjectsPage;
import com.zutubi.pulse.model.ProjectManager;
import com.zutubi.pulse.prototype.config.admin.GlobalConfiguration;
import com.zutubi.pulse.prototype.config.group.ServerPermission;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Vector;

/**
 */
public class AnonymousAccessAcceptanceTest extends SeleniumTestBase
{
    private static final String ANONYMOUS_GROUP_PATH = "groups/anonymous users";

    private static final String KEY_ANONYMOUS_ACCESS = "anonymousAccessEnabled";
    private static final String KEY_ANONYMOUS_SIGNUP = "anonymousSignupEnabled";

    private static final String ID_LOGIN         = "login";
    private static final String ID_DASHBOARD_TAB = "tab.dashboard";
    private static final String ID_PREFERENCES   = "prefs";
    private static final String ID_LOGOUT        = "logout";

    private static final String SIGNUP_INPUT_ACTION = "signup!input.action";

    protected void setUp() throws Exception
    {
        super.setUp();
        xmlRpcHelper.loginAsAdmin();
    }

    protected void tearDown() throws Exception
    {
        xmlRpcHelper.logout();
        super.tearDown();
    }

    public void testNoAnonymousAccess() throws Exception
    {
        ensureSetting(KEY_ANONYMOUS_ACCESS, false);
        goTo(urls.projects());

        // We should be denied access and redirected to the login page.
        LoginPage loginPage = new LoginPage(selenium, urls);
        loginPage.assertPresent();
    }

    public void testAnonymousSignup() throws Exception
    {
        ensureSetting(KEY_ANONYMOUS_ACCESS, false);
        ensureSetting(KEY_ANONYMOUS_SIGNUP, true);

        LoginPage loginPage = new LoginPage(selenium, urls);
        loginPage.goTo();
        assertTrue(loginPage.isSignupPresent());

        SignupForm form = loginPage.clickSignup();
        form.waitFor();
        form.saveFormElements("login_" + random, "name_" + random, "password", "password");

        WelcomePage welcomePage = new WelcomePage(selenium, urls);
        welcomePage.assertPresent();
        assertTextPresent("name_" + random);
        assertElementPresent(ID_DASHBOARD_TAB);
        assertElementPresent(ID_PREFERENCES);
        assertElementPresent(ID_LOGOUT);
        assertElementNotPresent(ID_LOGIN);
    }

    public void testAnonymousSignupDisabled() throws Exception
    {
        ensureSetting(KEY_ANONYMOUS_SIGNUP, false);
        LoginPage loginPage = new LoginPage(selenium, urls);
        loginPage.goTo();
        assertFalse(loginPage.isSignupPresent());
        goTo(SIGNUP_INPUT_ACTION);
        SignupForm form = new SignupForm(selenium);
        form.assertFormPresent();
        form.saveFormElements(random, random, "", "");
        assertTextPresent("Anonymous signup is not enabled");
    }

    public void testAnonymousSingupPasswordMismatch() throws Exception
    {
        ensureSetting(KEY_ANONYMOUS_SIGNUP, true);
        goTo(SIGNUP_INPUT_ACTION);
        SignupForm form = new SignupForm(selenium);
        form.assertFormPresent();
        form.saveFormElements(random, random, "p1", "p2");
        form.assertFormPresent();
        assertTextPresent("passwords do not match");
    }

    public void testAnonymousSingupExistingUser() throws Exception
    {
        ensureSetting(KEY_ANONYMOUS_SIGNUP, true);
        goTo(SIGNUP_INPUT_ACTION);
        SignupForm form = new SignupForm(selenium);
        form.assertFormPresent();
        form.saveFormElements("admin", "name", "p", "p");
        form.assertFormPresent();
        assertTextPresent("login 'admin' is already in use");
    }

    public void testAnonymousAccess() throws Exception
    {
        ensureSetting(KEY_ANONYMOUS_ACCESS, true);
        goTo(urls.projects());

        ProjectsPage projectsPage = new ProjectsPage(selenium, urls);
        projectsPage.assertPresent();
        assertElementPresent(ID_LOGIN);

        // No dashboard tab, user info or logout link for anonymous users
        assertElementNotPresent(ID_DASHBOARD_TAB);
        assertElementNotPresent(ID_PREFERENCES);
        assertElementNotPresent(ID_LOGOUT);
    }

    public void testAssignServerPermissionToAnonymousUsers() throws Exception
    {
        ensureSetting(KEY_ANONYMOUS_ACCESS, true);
        Hashtable<String, Object> group = xmlRpcHelper.getConfig(ANONYMOUS_GROUP_PATH);
        group.put("serverPermissions", new Vector(0));
        xmlRpcHelper.saveConfig(ANONYMOUS_GROUP_PATH, group, false);

        newSession();
        ProjectHierarchyPage hierarchyPage = new ProjectHierarchyPage(selenium, urls, ProjectManager.GLOBAL_PROJECT_NAME, true);
        hierarchyPage.goTo();
        assertFalse(hierarchyPage.isAddPresent());

        group.put("serverPermissions", new Vector<String>(Arrays.asList(ServerPermission.CREATE_PROJECT.toString())));
        xmlRpcHelper.saveConfig(ANONYMOUS_GROUP_PATH, group, false);
        
        newSession();
        hierarchyPage.goTo();
        waitForElement(ProjectHierarchyPage.LINK_ADD);
    }

    private void ensureSetting(String key, boolean enabled) throws Exception
    {
        Hashtable<String, Object> general = xmlRpcHelper.getConfig(GlobalConfiguration.SCOPE_NAME);
        if((Boolean)general.get(key) != enabled)
        {
            general.put(key, enabled);
            xmlRpcHelper.saveConfig(GlobalConfiguration.SCOPE_NAME, general, false);
            newSession();
        }
    }
}
