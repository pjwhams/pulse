package com.zutubi.pulse.acceptance.dependencies;

import static com.zutubi.pulse.acceptance.dependencies.ArtifactRepositoryTestUtils.clearArtifactRepository;
import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * This test suite includes all of the acceptance tests related to the
 * dependency feature set originally introduced in 2.1.
 *
 * These tests have been given there own suite as a way to
 * a) run them by themselves in development
 * b) help categorise the growing number of tests.
 */
public class DependenciesTestSuite
{
    public static Test suite()
    {
        TestSuite dependencySuite = new TestSuite();
        dependencySuite.addTestSuite(PublicationAndDependenciesAcceptanceTest.class);
//        dependencySuite.addTestSuite(AritfactRepositoryIsolationTest.class);

        // currently not working because the ant project is hard wired to use http://localhost:8080/repository.
//        dependencySuite.addTestSuite(ArtifactRepositoryAcceptanceTest.class);

        // cleanup the artifact repository before continueing on.
        return new CleanArtifactRepository(dependencySuite);
    }

    private static class CleanArtifactRepository extends TestSetup
    {
        private CleanArtifactRepository(Test test)
        {
            super(test);
        }

        protected void tearDown() throws Exception
        {
            clearArtifactRepository();

            super.tearDown();
        }
    }
}