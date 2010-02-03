package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.utils.*;
import com.zutubi.pulse.core.commands.ant.AntCommandConfiguration;
import com.zutubi.pulse.core.commands.maven2.Maven2CommandConfiguration;
import com.zutubi.pulse.core.engine.api.ResultState;

public class ArtifactRepositoryAcceptanceTest extends BaseXmlRpcAcceptanceTest
{
    private String random = null;
    private Repository repository = null;
    private ConfigurationHelper configurationHelper;
    private ProjectConfigurations projects;

    protected void setUp() throws Exception
    {
        super.setUp();

        random = randomName();

        xmlRpcHelper.loginAsAdmin();

        ConfigurationHelperFactory factory = new SingletonConfigurationHelperFactory();
        configurationHelper = factory.create(xmlRpcHelper);
        projects = new ProjectConfigurations(configurationHelper);

        repository = new Repository();
        repository.clean();
    }

    protected void tearDown() throws Exception
    {
        xmlRpcHelper.logout();
        
        super.tearDown();
    }

    // test that an external ivy process can publish to the internal artifact repository.
    public void testExternalIvyCanPublishToRepository() throws Exception
    {
        assertTrue(repository.isNotInRepository("zutubi/com.zutubi.sample/jars"));

        // run the ivyant build, verify that a new artifact is added to the repository.
        int buildNumber = createAndRunIvyAntProject("publish");

        // ensure that the build passed.
        assertEquals(ResultState.SUCCESS, getBuildStatus(random, buildNumber));

        assertTrue(repository.isInRepository("zutubi/com.zutubi.sample/jars"));
    }

    // test that an external ivy process can retrieve from the internal artifact repository.
    public void testExternalIvyCanRetrieveFromRepository() throws Exception
    {
        // create the expected artifact file.
        repository.createFile("zutubi/artifact/jars/artifact-1.0.0.jar");
        
        // artifact/jars/artifact-1.0.0.jar
        int buildNumber = createAndRunIvyAntProject("retrieve");

        // ensure that the build passed.
        assertEquals(ResultState.SUCCESS, getBuildStatus(random, buildNumber));
    }

    public void testExternalMavenCanUseRepository() throws Exception
    {
        String projectA = random + "A";
        int buildNumber = createAndRunMavenProject(projectA, "pom-artifact1.xml", "clean deploy");

        assertEquals(ResultState.SUCCESS, getBuildStatus(projectA, buildNumber));

        assertTrue(repository.isInRepository("zutubi/artifact1/maven-metadata.xml"));
        assertTrue(repository.isInRepository("zutubi/artifact1/1.0/artifact1-1.0.jar"));
        assertTrue(repository.isInRepository("zutubi/artifact1/1.0/artifact1-1.0.pom"));

        String projectB = random + "B";
        buildNumber = createAndRunMavenProject(projectB, "pom-artifact2.xml", "clean dependency:copy-dependencies");
        assertEquals(ResultState.SUCCESS, getBuildStatus(projectB, buildNumber));
    }

    private int createAndRunMavenProject(String projectName, String pom, String goals) throws Exception
    {
        DepMavenProject project = projects.createDepMavenProject(projectName);
        Maven2CommandConfiguration command = (Maven2CommandConfiguration) project.getRecipe("default").getConfig().getCommands().get("build");
        command.setPomFile(pom);
        command.setSettingsFile("settings.xml");
        command.setGoals(goals);

        configurationHelper.insertProject(project.getConfig());

        return xmlRpcHelper.runBuild(projectName);
    }

    private int createAndRunIvyAntProject(String target) throws Exception
    {
        IvyAntProject project = projects.createIvyAntProject(random);
        AntCommandConfiguration command = (AntCommandConfiguration) project.getRecipe("default").getConfig().getCommands().get("build");
        command.setTargets(target);
        
        configurationHelper.insertProject(project.getConfig());

        return xmlRpcHelper.runBuild(random);
    }
}
