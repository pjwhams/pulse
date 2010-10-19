package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.utils.*;
import com.zutubi.pulse.core.dependency.ivy.IvyStatus;
import com.zutubi.pulse.core.engine.api.ResultState;
import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Condition;
import com.zutubi.util.FileSystemUtils;
import com.zutubi.util.Predicate;

import java.io.File;
import java.util.Hashtable;
import java.util.Vector;

import static com.zutubi.pulse.core.dependency.ivy.IvyStatus.STATUS_MILESTONE;
import static com.zutubi.pulse.master.model.Project.State.IDLE;
import static com.zutubi.pulse.master.tove.config.project.DependencyConfiguration.*;
import static com.zutubi.util.CollectionUtils.asPair;

public class RebuildDependenciesAcceptanceTest extends BaseXmlRpcAcceptanceTest
{
    private File tmpDir;
    private String projectName;
    private BuildRunner buildRunner;
    private ConfigurationHelper configurationHelper;
    private ProjectConfigurations projects;

    protected void setUp() throws Exception
    {
        super.setUp();

        loginAsAdmin();

        Repository repository = new Repository();
        repository.clean();

        tmpDir = FileSystemUtils.createTempDir(randomName());

        projectName = randomName();

        buildRunner = new BuildRunner(xmlRpcHelper);

        ConfigurationHelperFactory factory = new SingletonConfigurationHelperFactory();
        configurationHelper = factory.create(xmlRpcHelper);

        projects = new ProjectConfigurations(configurationHelper);
    }

    @Override
    protected void tearDown() throws Exception
    {
        removeDirectory(tmpDir);

        logout();

        super.tearDown();
    }

    private ProjectConfigurationHelper insertProject(ProjectConfigurationHelper project) throws Exception
    {
        configurationHelper.insertProject(project.getConfig(), false);
        return project;
    }

    public void testRebuildSingleDependency() throws Exception
    {
        WaitProject projectA = projects.createWaitAntProject(projectName + "A", tmpDir);
        insertProject(projectA);

        WaitProject projectB = projects.createWaitAntProject(projectName + "B", tmpDir);
        projectB.addDependency(projectA);
        insertProject(projectB);

        buildRunner.triggerRebuild(projectB.getConfig());

        // expect projectA to be building, projectB to be pending_dependency.
        xmlRpcHelper.waitForBuildInProgress(projectA.getName(), 1);

        assertBuildQueued(projectB);

        projectA.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectA.getName(), 1);

        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectA.getName(), 1));

        // expect projectB to be building.
        xmlRpcHelper.waitForBuildInProgress(projectB.getName(), 1);
        projectB.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectB.getName(), 1);

        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));
    }

    public void testRebuildMultipleDependencies() throws Exception
    {
        final WaitProject projectA = projects.createWaitAntProject(projectName + "A", tmpDir);
        insertProject(projectA);

        final WaitProject projectB = projects.createWaitAntProject(projectName + "B", tmpDir);
        insertProject(projectB);

        WaitProject projectC = projects.createWaitAntProject(projectName + "C", tmpDir);
        projectC.addDependency(projectA);
        projectC.addDependency(projectB);
        insertProject(projectC);

        buildRunner.triggerRebuild(projectC.getConfig());

        WaitProject firstDependency;
        WaitProject secondDependency;
        AcceptanceTestUtils.waitForCondition(new Condition()
        {
            public boolean satisfied()
            {
                try
                {
                    return xmlRpcHelper.getBuildStatus(projectA.getName(), 1) == ResultState.IN_PROGRESS || xmlRpcHelper.getBuildStatus(projectB.getName(), 1) == ResultState.IN_PROGRESS;
                }
                catch (Exception e)
                {
                    return false;
                }
            }
        }, XmlRpcHelper.BUILD_TIMEOUT, "a dependency to start building");

        if (xmlRpcHelper.getBuildStatus(projectA.getName(), 1) == ResultState.IN_PROGRESS)
        {
            firstDependency = projectA;
            secondDependency = projectB;
        }
        else
        {
            firstDependency = projectB;
            secondDependency = projectA;
        }

        assertEquals(ResultState.PENDING, xmlRpcHelper.getBuildStatus(secondDependency.getName(), 1));
        assertBuildQueued(projectC);
        firstDependency.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(firstDependency.getName(), 1);

        xmlRpcHelper.waitForBuildInProgress(secondDependency.getName(), 1);
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(firstDependency.getName(), 1));
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(secondDependency.getName(), 1));
        assertBuildQueued(projectC);
        secondDependency.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(secondDependency.getName(), 1);

        xmlRpcHelper.waitForBuildInProgress(projectC.getName(), 1);
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectA.getName(), 1));
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectC.getName(), 1));
        projectC.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectC.getName(), 1);
    }

    public void testRebuildTransitiveDependency() throws Exception
    {
        final WaitProject projectA = projects.createWaitAntProject(projectName + "A", tmpDir);
        insertProject(projectA);

        final WaitProject projectB = projects.createWaitAntProject(projectName + "B", tmpDir);
        projectB.addDependency(projectA);
        insertProject(projectB);

        final WaitProject projectC = projects.createWaitAntProject(projectName + "C", tmpDir);
        projectC.addDependency(projectB);
        insertProject(projectC);

        buildRunner.triggerRebuild(projectC.getConfig());

        xmlRpcHelper.waitForBuildInProgress(projectA.getName(), 1);

        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectA.getName(), 1));
        assertBuildQueued(projectB);
        assertBuildQueued(projectC);

        projectA.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectA.getName(), 1);
        xmlRpcHelper.waitForBuildInProgress(projectB.getName(), 1);

        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectA.getName(), 1));
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));
        assertBuildQueued(projectC);

        projectB.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectB.getName(), 1);
        xmlRpcHelper.waitForBuildInProgress(projectC.getName(), 1);

        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectA.getName(), 1));
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectC.getName(), 1));

        projectC.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectC.getName(), 1);
    }

    public void testRebuildUsesTransitiveProperty() throws Exception
    {
        final WaitProject projectA = projects.createWaitAntProject(projectName + "A", tmpDir);
        insertProject(projectA);
        // even though project a is not rebuilt as part of the rebuild, project b does depend on it
        // so we require it to have been built at least once for project b to be successful.
        buildRunner.triggerSuccessfulBuild(projectA);

        final WaitProject projectB = projects.createWaitAntProject(projectName + "B", tmpDir);
        projectB.addDependency(projectA);
        insertProject(projectB);

        final WaitProject projectC = projects.createWaitAntProject(projectName + "C", tmpDir);
        projectC.addDependency(projectB).setTransitive(false);
        insertProject(projectC);

        buildRunner.triggerRebuild(projectC.getConfig());

        xmlRpcHelper.waitForBuildInProgress(projectB.getName(), 1);

        assertEquals(IDLE, xmlRpcHelper.getProjectState(projectA.getName()));
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));
        assertBuildQueued(projectC);

        projectB.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectB.getName(), 1);
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));

        xmlRpcHelper.waitForBuildInProgress(projectC.getName(), 1);

        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectC.getName(), 1));

        projectC.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectC.getName(), 1);
    }

    public void testRebuildUsesStatusProperty() throws Exception
    {
        final WaitProject projectA = projects.createWaitAntProject(projectName + "A", tmpDir);
        projectA.getConfig().getDependencies().setStatus(IvyStatus.STATUS_RELEASE);
        insertProject(projectA);
        buildRunner.triggerSuccessfulBuild(projectA);

        final WaitProject projectB = projects.createWaitAntProject(projectName + "B", tmpDir);
        projectB.addDependency(projectA).setRevision(REVISION_LATEST_RELEASE);
        insertProject(projectB);

        final WaitProject projectC = projects.createWaitAntProject(projectName + "C", tmpDir);
        projectC.addDependency(projectB).setRevision(REVISION_LATEST_MILESTONE);
        insertProject(projectC);

        final WaitProject projectD = projects.createWaitAntProject(projectName + "D", tmpDir);
        projectD.addDependency(projectC).setRevision(REVISION_LATEST_INTEGRATION);
        insertProject(projectD);

        buildRunner.triggerRebuild(projectD.getConfig(), asPair("status", (Object) STATUS_MILESTONE));

        xmlRpcHelper.waitForBuildInProgress(projectB.getName(), 1);

        assertEquals(IDLE, xmlRpcHelper.getProjectState(projectA.getName()));
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));
        assertBuildQueued(projectC);
        assertBuildQueued(projectD);

        projectB.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectB.getName(), 1);
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectB.getName(), 1));

        xmlRpcHelper.waitForBuildInProgress(projectC.getName(), 1);
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectC.getName(), 1));
        assertBuildQueued(projectD);

        projectC.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectC.getName(), 1);
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectC.getName(), 1));

        xmlRpcHelper.waitForBuildInProgress(projectD.getName(), 1);
        assertEquals(ResultState.IN_PROGRESS, xmlRpcHelper.getBuildStatus(projectD.getName(), 1));

        projectD.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectD.getName(), 1);
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectD.getName(), 1));
    }

    public void testRebuildStopsOnFailure() throws Exception
    {
        ProjectConfigurationHelper projectA = projects.createFailAntProject(projectName + "A");
        insertProject(projectA);

        WaitProject projectB = projects.createWaitAntProject(projectName + "B", tmpDir);
        projectB.addDependency(projectA);
        insertProject(projectB);

        buildRunner.triggerRebuild(projectB.getConfig());

        xmlRpcHelper.waitForBuildToComplete(projectA.getName(), 1);
        assertEquals(ResultState.FAILURE, xmlRpcHelper.getBuildStatus(projectA.getName(), 1));

        assertNull(xmlRpcHelper.getBuildStatus(projectB.getName(), 1));

        // We would normally have to release projectBs' build.  However, it did not run,
        // because projectA failed. 
    }

    public void testTransientArtifactDeliveryForMetaBuild() throws Exception
    {
        // require 2 agents for concurrent builds of project A and project B.
        xmlRpcHelper.ensureAgent(SeleniumTestBase.AGENT_NAME);
        // Ensure that the agent is online and available for the build.  Starting the
        // build without the agent available will result in hung builds.
        xmlRpcHelper.waitForAgentToBeIdle(SeleniumTestBase.AGENT_NAME);

        // project A -> project B -> project C
        DepAntProject projectA = projects.createDepAntProject(projectName + "A");
        projectA.addArtifacts("build/artifact.jar");
        projectA.addFilesToCreate("build/artifact.jar");
        projectA.clearTriggers();                   // do not want dependency trigger firing.
        projectA.getDefaultStage().setAgent(null);  // allow project A to run on any agent.
        insertProject(projectA);

        WaitProject projectB = projects.createWaitAntProject(projectName + "B", tmpDir);
        projectB.addDependency(projectA);
        projectB.clearTriggers();                   // do not want dependency trigger firing.
        insertProject(projectB);

        DepAntProject projectC = projects.createDepAntProject(projectName + "C");
        projectC.addDependency(projectB);
         // ensure we see the revision of the artifact being delivered
        projectC.getConfig().getDependencies().setRetrievalPattern("lib/[artifact]-[revision].[ext]");
        projectC.clearTriggers();                   // do not want dependency trigger firing.
        projectC.addExpectedFiles("lib/artifact-1.jar");
        projectC.addNotExpectedFiles("lib/artifact-2.jar");
        insertProject(projectC);

        buildRunner.triggerRebuild(projectC);

        xmlRpcHelper.waitForBuildToComplete(projectA.getName(), 1);
        xmlRpcHelper.waitForBuildInProgress(projectB.getName(), 1);

        buildRunner.triggerSuccessfulBuild(projectA);

        projectB.releaseBuild();
        xmlRpcHelper.waitForBuildToComplete(projectB.getName(), 1);
        xmlRpcHelper.waitForBuildToComplete(projectC.getName(), 1);

        // ensure that project C uses project A build 1 artifacts.
        assertEquals(ResultState.SUCCESS, xmlRpcHelper.getBuildStatus(projectC.getName(), 1));
    }

    private void assertBuildQueued(final ProjectConfigurationHelper project) throws Exception
    {
        assertNotNull(getQueuedBuild(project));
    }

    private Hashtable<String, Object> getQueuedBuild(final ProjectConfigurationHelper project)
            throws Exception
    {
        Vector<Hashtable<String, Object>> snapshot = xmlRpcHelper.getBuildQueueSnapshot();
        return CollectionUtils.find(snapshot, new Predicate<Hashtable<String, Object>>()
        {
            public boolean satisfied(Hashtable<String, Object> build)
            {
                return build.get("project").equals(project.getName());
            }
        });
    }
}
