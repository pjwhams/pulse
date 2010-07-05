package com.zutubi.pulse.acceptance;

import static com.zutubi.pulse.acceptance.Constants.Project.Cleanup.*;
import com.zutubi.pulse.acceptance.forms.admin.CleanupForm;
import com.zutubi.pulse.acceptance.pages.admin.CleanupRulesPage;
import com.zutubi.pulse.acceptance.pages.admin.ProjectConfigPage;
import static com.zutubi.util.CollectionUtils.asPair;

/**
 * The acceptance tests that run through the cleanup web ui.
 */
public class CleanupUIAcceptanceTest extends SeleniumTestBase
{
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

         xmlRpcHelper.loginAsAdmin();
    }

    @Override
    protected void tearDown() throws Exception
    {
        xmlRpcHelper.logout();

        super.tearDown();
    }

    public void testCreateNewCleanupRule()
    {
        addProject(random, true);

        browser.loginAsAdmin();

        ProjectConfigPage projectPage = browser.openAndWaitFor(ProjectConfigPage.class, random, false);

        CleanupRulesPage cleanupRulesPage = projectPage.clickCleanupAndWait();
        cleanupRulesPage.clickAdd();

        CleanupForm cleanup = browser.createForm(CleanupForm.class);
        cleanup.waitFor();
        cleanup.finishNamedFormElements(asPair(NAME, "new rule"), asPair(RETAIN, "1"), asPair(CLEANUP_ALL, "true"));

        cleanupRulesPage.openAndWaitFor();
        cleanupRulesPage.waitFor();
        assertTrue(cleanupRulesPage.isItemPresent("new rule"));
    }
}