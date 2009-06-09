package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.forms.ConfigurationForm;
import com.zutubi.pulse.acceptance.forms.admin.SelectTypeState;
import com.zutubi.pulse.acceptance.pages.admin.CompositePage;
import com.zutubi.pulse.acceptance.pages.admin.ListPage;
import com.zutubi.pulse.acceptance.pages.browse.BuildSummaryPage;
import com.zutubi.pulse.core.model.RecipeResult;
import com.zutubi.pulse.master.model.BuildResult;
import com.zutubi.pulse.master.tove.config.MasterConfigurationRegistry;
import com.zutubi.pulse.master.tove.config.project.BuildSelectorConfiguration;
import com.zutubi.pulse.master.tove.config.project.hooks.*;
import com.zutubi.tove.type.record.PathUtils;
import static com.zutubi.util.CollectionUtils.asPair;
import com.zutubi.util.FileSystemUtils;
import com.zutubi.util.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

/**
 * Tests for build hooks, both configuration and ensuring they are executed
 * when expected.
 */
public class BuildHookAcceptanceTest extends SeleniumTestBase
{
    private static final int TASK_TIMEOUT = 30000;

    private static final String PROJECT_NAME = "hook-test-project";
    private static final String PROJECT_PATH = PathUtils.getPath(MasterConfigurationRegistry.PROJECTS_SCOPE, "hook-test-project");
    private static final String HOOKS_BASENAME = "buildHooks";
    private static final String HOOKS_PATH   = PathUtils.getPath(PROJECT_PATH, HOOKS_BASENAME);

    private File tempDir;
    private File dumpEnv;

    protected void setUp() throws Exception
    {
        super.setUp();
        tempDir = FileSystemUtils.createTempDir(BuildHookAcceptanceTest.class.getName(), "");
        dumpEnv = copyInputToDirectory("dumpenv", "jar", tempDir);

        xmlRpcHelper.loginAsAdmin();
        xmlRpcHelper.ensureProject(PROJECT_NAME);
        xmlRpcHelper.deleteAllConfigs(PathUtils.getPath(HOOKS_PATH, PathUtils.WILDCARD_ANY_ELEMENT));
        loginAsAdmin();
    }

    protected void tearDown() throws Exception
    {
        xmlRpcHelper.logout();
        FileSystemUtils.rmdir(tempDir);
        super.tearDown();
    }

    public void testPreBuildHook() throws Exception
    {
        chooseHookType("zutubi.preBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PreBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        addTask("${project} ${status}");

        xmlRpcHelper.runBuild(PROJECT_NAME);
        assertArgs(PROJECT_NAME, "${status}");
    }

    public void testPostBuildHook() throws Exception
    {
        postBuildHelper();
        addTask("${project} ${status}");

        xmlRpcHelper.runBuild(PROJECT_NAME);
        assertArgs(PROJECT_NAME, "success");
    }

    public void testBuildDirProperty() throws Exception
    {
        postBuildHelper();
        addTask("${build.dir}");

        xmlRpcHelper.runBuild(PROJECT_NAME);
        List<String> args = getArgs();

        // The build directory contains the build log.
        File log = new File(args.get(0), BuildResult.BUILD_LOG);
        assertTrue(log.isFile());
    }

    public void testPostBuildHookCanAccessProjectProperty() throws Exception
    {
        xmlRpcHelper.insertSimpleProject(random, false);
        xmlRpcHelper.insertProjectProperty(random, "some.property", "some.value");

        chooseHookType(random, "zutubi.postBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PostBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        selectFromAllTasks();
        addTask(random, "${project} ${some.property}");

        xmlRpcHelper.runBuild(random);
        assertArgs(random, "some.value");
    }

    public void testPostBuildHookCanAccessTriggerProperty() throws Exception
    {
        xmlRpcHelper.insertSimpleProject(random, false);
        // Also add the property to the project - we want to make sure it is
        // overridden by the value passed on trigger.
        xmlRpcHelper.insertProjectProperty(random, "some.property", "some.value");

        chooseHookType(random, "zutubi.postBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PostBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        selectFromAllTasks();
        addTask(random, "${some.property}");

        Hashtable<String, String> properties = new Hashtable<String, String>();
        properties.put("some.property", "trigger.value");
        int number = xmlRpcHelper.getNextBuildNumber(random);
        xmlRpcHelper.triggerBuild(random, "", properties);
        xmlRpcHelper.waitForBuildToComplete(random, number);
        assertArgs("trigger.value");
    }

    public void testPostStageHook() throws Exception
    {
        postStageHelper();
        addTask("${project} ${stage} ${recipe} ${status}");

        xmlRpcHelper.runBuild(PROJECT_NAME);
        assertArgs(PROJECT_NAME, "default", "[default]", "success");
    }

    public void testStageDirProperty() throws Exception
    {
        postStageHelper();
        addTask("${stage.dir}");

        xmlRpcHelper.runBuild(PROJECT_NAME);
        List<String> args = getArgs();

        // The stage directory contains the recipe log.
        File log = new File(args.get(0), RecipeResult.RECIPE_LOG);
        assertTrue(log.isFile());
    }

    public void testManualHook() throws Exception
    {
        chooseHookType("zutubi.manualBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, ManualBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        selectFromAllTasks();

        CompositePage hookPage = addTask("${build.number} ${project} ${status}");
        int buildNumber = xmlRpcHelper.runBuild(PROJECT_NAME);
        triggerHook(hookPage, buildNumber);
        assertArgs(Long.toString(buildNumber), PROJECT_NAME, "success");
    }

    public void testManuallyTriggerPreBuildHook() throws Exception
    {
        int buildNumber = xmlRpcHelper.runBuild(PROJECT_NAME);

        chooseHookType("zutubi.preBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PreBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        CompositePage hookPage = addTask("${project} ${status}");
        triggerHook(hookPage, buildNumber);
        assertArgs(PROJECT_NAME, "success");
    }

    public void testManualTriggerNotAvailableWhenDisabled() throws Exception
    {
        long buildNumber = xmlRpcHelper.runBuild(PROJECT_NAME);

        chooseHookType("zutubi.preBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PreBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random), asPair("allowManualTrigger", "false"));

        CompositePage hookPage = addTask("${project} ${status}");
        assertFalse(hookPage.isActionPresent(BuildHookConfigurationActions.ACTION_TRIGGER));

        BuildSummaryPage summaryPage = browser.openAndWaitFor(BuildSummaryPage.class, PROJECT_NAME, buildNumber);
        assertFalse(summaryPage.isHookPresent(random));
    }

    public void testManuallyTriggerPostStageHook() throws Exception
    {
        int buildNumber = xmlRpcHelper.runBuild(PROJECT_NAME);

        postStageHelper();
        CompositePage hookPage = addTask("${project} ${stage} ${recipe} ${status}");
        triggerHook(hookPage, buildNumber);
        assertArgs(PROJECT_NAME, "default", "[default]", "success");
    }

    public void testFailOnError() throws Exception
    {
        chooseHookType("zutubi.postBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PostBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random), asPair("failOnError", "true"));

        selectFromAllTasks();
        ConfigurationForm taskForm = browser.createForm(ConfigurationForm.class, RunExecutableTaskConfiguration.class);
        taskForm.waitFor();
        taskForm.finishFormElements("nosuchexe", null, tempDir.getAbsolutePath(), null, null);

        waitForHook(PROJECT_NAME);
        int buildNumber = xmlRpcHelper.runBuild(PROJECT_NAME);
        Hashtable<String,Object> build = xmlRpcHelper.getBuild(PROJECT_NAME, buildNumber);
        assertEquals("error", build.get("status"));
    }

    public void testRestrictToStates() throws Exception
    {
        chooseHookType("zutubi.postBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PostBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random), asPair("runForAll", "false"), asPair("runForStates", "error"));

        selectFromAllTasks();
        addTask("${project}");

        xmlRpcHelper.runBuild(PROJECT_NAME);
        assertFalse(new File(tempDir, "args.txt").exists());
    }

    public void testDisable() throws Exception
    {
        postBuildHelper();
        CompositePage hookPage = addTask("${project} ${status}");
        hookPage.clickActionAndWait("disable");
        assertEquals("disabled", hookPage.getStateField("state"));

        xmlRpcHelper.runBuild(PROJECT_NAME);
        assertFalse(new File(tempDir, "args.txt").exists());
    }

    public void testTriggerFromBuildPage() throws Exception
    {
        chooseHookType("zutubi.manualBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, ManualBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        selectFromAllTasks();

        addTask("${build.number} ${project} ${status}");
        long buildNumber = xmlRpcHelper.runBuild(PROJECT_NAME);

        BuildSummaryPage summaryPage = browser.openAndWaitFor(BuildSummaryPage.class, PROJECT_NAME, buildNumber);
        summaryPage.clickHook(random);
        browser.waitForVisible("status-message");
        assertTextPresent("triggered hook '" + random + "'");

        waitForTask();
        assertArgs(Long.toString(buildNumber), PROJECT_NAME, "success");
    }

    private void postBuildHelper()
    {
        chooseHookType("zutubi.postBuildHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PostBuildHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        selectFromAllTasks();
    }

    private void postStageHelper()
    {
        chooseHookType("zutubi.postStageHookConfig");

        ConfigurationForm hookForm = browser.createForm(ConfigurationForm.class, PostStageHookConfiguration.class);
        hookForm.waitFor();
        hookForm.nextNamedFormElements(asPair("name", random));

        selectFromStageTasks();
    }

    private void triggerHook(CompositePage hookPage, int buildNumber) throws InterruptedException
    {
        File envFile = new File(tempDir, "env.txt");
        assertFalse(envFile.exists());

        hookPage.openAndWaitFor();
        hookPage.clickAction("trigger");
        ConfigurationForm buildForm = browser.createForm(ConfigurationForm.class, BuildSelectorConfiguration.class);
        buildForm.waitFor();
        String number = Long.toString(buildNumber);
        buildForm.saveFormElements(number);

        waitForTask();
    }

    private void waitForTask() throws InterruptedException
    {
        File envFile = new File(tempDir, "env.txt");
        long startTime = System.currentTimeMillis();
        while(!envFile.exists())
        {
            assertTrue(System.currentTimeMillis() - startTime < TASK_TIMEOUT);
            Thread.sleep(500);
        }
    }

    private SelectTypeState chooseHookType(String symbolicName)
    {
        return chooseHookType(PROJECT_NAME, symbolicName);
    }

    private SelectTypeState chooseHookType(String projectName, String symbolicName)
    {
        ListPage hooksPage = browser.openAndWaitFor(ListPage.class, getHooksPath(projectName));
        hooksPage.clickAdd();

        SelectTypeState hookType = new SelectTypeState(browser.getSelenium());
        hookType.waitFor();
        assertEquals(Arrays.asList("zutubi.manualBuildHookConfig", "zutubi.postBuildHookConfig", "zutubi.postStageHookConfig", "zutubi.preBuildHookConfig"), hookType.getSortedOptionList());
        hookType.nextFormElements(symbolicName);
        return hookType;
    }

    private String getHooksPath(String projectName)
    {
        return PathUtils.getPath(MasterConfigurationRegistry.PROJECTS_SCOPE, projectName, HOOKS_BASENAME);
    }

    private void selectFromAllTasks()
    {
        SelectTypeState taskType = new SelectTypeState(browser.getSelenium());
        taskType.waitFor();
        assertEquals(Arrays.asList("zutubi.emailCommittersTaskConfig", "zutubi.runExecutableTaskConfig", "zutubi.tagTaskConfig"), taskType.getSortedOptionList());
        taskType.nextFormElements("zutubi.runExecutableTaskConfig");
    }

    private void selectFromStageTasks()
    {
        SelectTypeState taskType = new SelectTypeState(browser.getSelenium());
        taskType.waitFor();
        assertEquals(Arrays.asList("zutubi.runExecutableTaskConfig", "zutubi.tagTaskConfig"), taskType.getSortedOptionList());
        taskType.nextFormElements("zutubi.runExecutableTaskConfig");
    }

    private CompositePage addTask(String arguments)
    {
        return addTask(PROJECT_NAME, arguments);
    }

    private CompositePage addTask(String projectName, String arguments)
    {
        ConfigurationForm taskForm = browser.createForm(ConfigurationForm.class, RunExecutableTaskConfiguration.class);
        taskForm.waitFor();
        taskForm.finishFormElements("java", "-jar \"" + dumpEnv.getAbsolutePath().replace('\\', '/') + "\" " + arguments, tempDir.getAbsolutePath(), null, null);
        return waitForHook(projectName);
    }

    private CompositePage waitForHook(String projectName)
    {
        return browser.openAndWaitFor(CompositePage.class, PathUtils.getPath(getHooksPath(projectName), random));
    }

    private List<String> getArgs() throws IOException
    {
        File argFile = new File(tempDir, "args.txt");
        String args = IOUtils.fileToString(argFile);
        return Arrays.asList(args.split("\\r?\\n"));
    }

    private void assertArgs(String... expected) throws IOException
    {
        List<String> gotArgs = getArgs();
        assertEquals(Arrays.asList(expected), gotArgs);
    }
}
