package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.forms.admin.DependencyForm;
import com.zutubi.pulse.acceptance.forms.admin.TriggerBuildForm;
import com.zutubi.pulse.acceptance.pages.admin.ProjectConfigPage;
import com.zutubi.pulse.acceptance.pages.admin.ProjectDependenciesPage;
import com.zutubi.pulse.acceptance.pages.browse.ProjectHomePage;
import com.zutubi.pulse.acceptance.utils.*;
import com.zutubi.pulse.core.dependency.ivy.IvyLatestRevisionMatcher;
import com.zutubi.pulse.core.dependency.ivy.IvyStatus;
import com.zutubi.pulse.master.tove.config.MasterConfigurationRegistry;
import com.zutubi.pulse.master.tove.config.project.BuildStageConfiguration;
import com.zutubi.pulse.master.tove.config.project.DependencyConfiguration;
import com.zutubi.tove.type.record.PathUtils;
import static com.zutubi.util.CollectionUtils.asPair;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * A set of acceptance tests focused on the dependency systems UI.
 */
public class DependenciesConfigurationAcceptanceTest extends SeleniumTestBase
{
    private ConfigurationHelper configurationHelper;
    private ProjectConfigurations projects;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        xmlRpcHelper.loginAsAdmin();

        ConfigurationHelperFactory factory = new SingletonConfigurationHelperFactory();
        configurationHelper = factory.create(xmlRpcHelper);

        projects = new ProjectConfigurations(configurationHelper);
    }

    @Override
    protected void tearDown() throws Exception
    {
        xmlRpcHelper.logout();

        super.tearDown();
    }

    private void insertProject(ProjectConfigurationHelper project) throws Exception
    {
        configurationHelper.insertProject(project.getConfig());
    }

    public void testDependencyCanNotReferenceOwningProject() throws Exception
    {
        String projectName = random;

        // create project
        String path = addProject(projectName, true);

        String projectHandle = xmlRpcHelper.getConfigHandle(path);

        loginAsAdmin();

        // go to add dependency page.
        ProjectConfigPage projectPage = browser.openAndWaitFor(ProjectConfigPage.class, projectName, false);

        ProjectDependenciesPage projectDependenciesPage = projectPage.clickDependenciesAndWait();
        DependencyForm form = projectDependenciesPage.clickAdd();
        form.waitFor();

        assertThat(form.getProjectOptions(), not(hasItem(projectHandle)));
    }

    public void testDependencyStageOptionsBelongToSelectedProject() throws Exception
    {
        String projectA = random + "A";
        String projectB = random + "B";
        String projectC = random + "C";

        addProject(projectA, true);
        addProject(projectB, true);
        addProject(projectC, true);

        addStages(projectA, "a1", "a2", "a3", "a4");
        addStages(projectB, "b1", "b2");

        loginAsAdmin();

        ProjectConfigPage projectPage = browser.openAndWaitFor(ProjectConfigPage.class, projectC, false);

        ProjectDependenciesPage projectDependenciesPage = projectPage.clickDependenciesAndWait();
        DependencyForm form = projectDependenciesPage.clickAdd();
        form.waitFor();

        // We expect no initial stage option values since no project is selected.
        List<String> optionValues = form.getStagesOptionValues();
        assertEquals(0, optionValues.size());

        assertExpectedStageOptions(form, projectA, "a1", "a2", "a3", "a4", "default");
        assertExpectedStageOptions(form, projectB, "b1", "b2", "default");

        form.finishNamedFormElements(
                asPair("project", getProjectHandle(projectB)),
                asPair("stageType", DependencyConfiguration.StageType.SELECTED_STAGES.name()),
                asPair("stages", getStageHandles(projectB, "b1")[0])
        );
        projectDependenciesPage.waitFor();

        assertDependenciesTableRow(projectDependenciesPage, 1, projectB, "latest.integration", "b1", "true");

        Vector<String> listing = xmlRpcHelper.getConfigListing("projects/" + projectC + "/dependencies/dependencies");
        String dependencyName = listing.get(0);

        form = projectDependenciesPage.clickView(dependencyName);
        form.waitFor();

        assertExpectedStageOptions(form, projectA, "a1", "a2", "a3", "a4", "default");
        form.saveNamedFormElements(
                asPair("project", getProjectHandle(projectA)),
                asPair("stageType", DependencyConfiguration.StageType.SELECTED_STAGES.name()),
                asPair("stages", getStageHandles(projectA, "a1")[0])
        );
        projectDependenciesPage.waitFor();

        assertDependenciesTableRow(projectDependenciesPage, 1, projectA, "latest.integration", "a1", "true");
    }

    public void testCircularDependencyCheck() throws Exception
    {
        DepAntProject projectA = projects.createDepAntProject(random + "A");
        insertProject(projectA);

        DepAntProject projectB = projects.createDepAntProject(random + "B");
        projectB.addDependency(projectA).setTransitive(true);
        insertProject(projectB);

        DepAntProject projectC = projects.createDepAntProject(random + "C");
        projectC.addDependency(projectB);
        insertProject(projectC);

        loginAsAdmin();

        ProjectConfigPage projectPage = browser.openAndWaitFor(ProjectConfigPage.class, projectA.getName(), false);

        ProjectDependenciesPage projectDependenciesPage = projectPage.clickDependenciesAndWait();
        DependencyForm form = projectDependenciesPage.clickAdd(); // takes us to the wizard version of the dependency form.
        form.waitFor();

        form.finishNamedFormElements(asPair("project", String.valueOf(projectC.getConfig().getHandle())));
        assertTrue(form.isFormPresent());
        assertTrue(form.getFieldErrorMessage("project").contains("circular dependency detected"));
    }

    public void testRebuildCheckboxAppearsOnManualTrigger() throws Exception
    {
        String projectA = random + "A";
        String projectB = random + "B";

        addProject(projectA, true);
        addProject(projectB, true);
        xmlRpcHelper.enableBuildPrompting(projectB);

        loginAsAdmin();

        assertFalse(isRebuildOptionAvailableOnPrompt(projectB, false));

        // add a dependency to the project.
        addDependency(projectB, projectA);

        assertTrue(isRebuildOptionAvailableOnPrompt(projectB, true));
    }

    private boolean isRebuildOptionAvailableOnPrompt(String projectName, boolean expected)
    {
        ProjectHomePage home = browser.openAndWaitFor(ProjectHomePage.class, projectName);
        home.triggerBuild();

        TriggerBuildForm form = browser.createForm(TriggerBuildForm.class);
        if (expected)
        {
            form.expectRebuildField();
        }
        form.waitFor();
        assertTrue(form.isFormPresent());
        return form.isRebuildCheckboxPresent();
    }

    public void testRebuildOptionAvailabilityOnProjectHomePage() throws Exception
    {
        String upstreamProject1 = random + "-upstream1";
        String upstreamProject2 = random + "-upstream2";
        String downstreamIntegrationProject = random + "-downstream-integration";
        String downstreamMilestoneProject = random + "-downstream-milestone";
        String downstreamBothProject = random + "-downstream-both";

        addProject(upstreamProject1, true);
        addProject(upstreamProject2, true);

        addProject(downstreamIntegrationProject, true);
        addDependency(downstreamIntegrationProject, upstreamProject1);

        addProject(downstreamMilestoneProject, true);
        addDependency(downstreamMilestoneProject, upstreamProject1, IvyStatus.STATUS_MILESTONE);

        addProject(downstreamBothProject, true);
        addDependency(downstreamBothProject, upstreamProject1);
        addDependency(downstreamBothProject, upstreamProject2, IvyStatus.STATUS_MILESTONE);

        loginAsAdmin();

        assertFalse(isRebuildActionAvailableOnProjectHomePage(upstreamProject1));
        assertTrue(isRebuildActionAvailableOnProjectHomePage(downstreamIntegrationProject));
        assertFalse(isRebuildActionAvailableOnProjectHomePage(downstreamMilestoneProject));
        assertTrue(isRebuildActionAvailableOnProjectHomePage(downstreamBothProject));

        turnOnPromptOption(downstreamMilestoneProject);
        assertTrue(isRebuildActionAvailableOnProjectHomePage(downstreamMilestoneProject));
    }

    private boolean isRebuildActionAvailableOnProjectHomePage(String projectName)
    {
        ProjectHomePage home = browser.openAndWaitFor(ProjectHomePage.class, projectName);
        return home.isRebuildActionPresent();
    }

    private void turnOnPromptOption(String project) throws Exception
    {
        String optionsPath = PathUtils.getPath(MasterConfigurationRegistry.PROJECTS_SCOPE, project, Constants.Project.OPTIONS);
        Hashtable<String, Object> options = xmlRpcHelper.getConfig(optionsPath);
        options.put("prompt", true);
        xmlRpcHelper.saveConfig(optionsPath, options, false);
    }

    private void assertDependenciesTableRow(ProjectDependenciesPage page, int rowIndex, String project, String revision, String stages, String transitive)
    {
        ProjectDependenciesPage.DependencyRow row = page.getDependencyRow(rowIndex);
        assertEquals(project, row.getProjectName());
        assertEquals(revision, row.getRevision());
        assertEquals(stages, row.getStageList());
        assertEquals(transitive, row.getTransitive());
    }

    private void assertExpectedStageOptions(DependencyForm form, String projectName, String... expectedStages) throws Exception
    {
        String projectHandle = getProjectHandle(projectName);
        form.setProject(projectHandle);
        List<String> optionValues = form.getStagesOptionValues();
        // Along with the expected stages, we also expect an empty option
        assertEquals(expectedStages.length + 1, optionValues.size());
        assertEquals(optionValues.get(0), "0");
        assertThat(optionValues, hasItems(getStageHandles(projectName, expectedStages)));
    }

    private String getProjectHandle(String name) throws Exception
    {
        return xmlRpcHelper.getConfigHandle("projects/" + name);
    }

    private String[] getStageHandles(String projectName, String... stageNames) throws Exception
    {
        List<String> handles = new LinkedList<String>();
        for (String stageName : stageNames)
        {
            handles.add(xmlRpcHelper.getConfigHandle("projects/" + projectName + "/stages/" + stageName));
        }
        return handles.toArray(new String[handles.size()]);
    }

    private void addStages(String projectName, String... stageNames) throws Exception
    {
        for (String stageName : stageNames)
        {
            Hashtable<String, Object> stage = xmlRpcHelper.createDefaultConfig(BuildStageConfiguration.class);
            stage.put("name", stageName);
            xmlRpcHelper.insertConfig("projects/" + projectName + "/stages", stage);
        }
    }

    private void addDependency(String projectFrom, String projectTo) throws Exception
    {
        addDependency(projectFrom, projectTo, IvyStatus.STATUS_INTEGRATION);
    }

    private void addDependency(String projectFrom, String projectTo, String status) throws Exception
    {
        // configure the default stage.
        String projectDependenciesPath = "projects/" + projectFrom + "/dependencies";

        Hashtable<String, Object> projectDependencies = xmlRpcHelper.getConfig(projectDependenciesPath);
        if (!projectDependencies.containsKey("dependencies"))
        {
            projectDependencies.put("dependencies", new Vector<Hashtable<String, Object>>());
        }

        @SuppressWarnings("unchecked")
        Vector<Hashtable<String, Object>> dependencies = (Vector<Hashtable<String, Object>>) projectDependencies.get("dependencies");
        Hashtable<String, Object> dependency = xmlRpcHelper.createEmptyConfig(DependencyConfiguration.class);
        dependency.put("project", "projects/" + projectTo);
        dependency.put("revision", IvyLatestRevisionMatcher.LATEST + status);

        dependencies.add(dependency);

        xmlRpcHelper.saveConfig(projectDependenciesPath, projectDependencies, true);
    }
}
