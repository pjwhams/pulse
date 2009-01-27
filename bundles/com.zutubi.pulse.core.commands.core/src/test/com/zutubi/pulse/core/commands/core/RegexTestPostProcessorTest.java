package com.zutubi.pulse.core.commands.core;

import com.zutubi.pulse.core.postprocessors.api.TestCaseResult;
import com.zutubi.pulse.core.postprocessors.api.TestPostProcessorTestCase;
import com.zutubi.pulse.core.postprocessors.api.TestStatus;
import static com.zutubi.pulse.core.postprocessors.api.TestStatus.*;
import com.zutubi.pulse.core.postprocessors.api.TestSuiteResult;

import java.io.IOException;

public class RegexTestPostProcessorTest extends TestPostProcessorTestCase
{
    private static final String EXTENSION = "txt";

    public void testSmokeTest() throws IOException
    {
        TestSuiteResult tests = runProcessorAndGetTests(createProcessor(), EXTENSION);
        assertEquals(5, tests.getTotalWithStatus(FAILURE));
        assertEquals(91, tests.getTotal());
        assertEquals(0, tests.getTotalWithStatus(ERROR));
    }

    public void testAutoFail() throws IOException
    {
        TestSuiteResult tests = runProcessorAndGetTests(createProcessor(true, -1), EXTENSION);
        assertEquals(5, tests.getTotal());
        assertEquals(3, tests.getTotalWithStatus(FAILURE));
        assertEquals(1, tests.getTotalWithStatus(ERROR));
        assertEquals(PASS, tests.findCase("test1").getStatus());
        assertEquals(ERROR, tests.findCase("test2").getStatus());
        assertEquals(FAILURE, tests.findCase("test3").getStatus());
        assertEquals(FAILURE, tests.findCase("test4").getStatus());
        assertEquals(FAILURE, tests.findCase("test5").getStatus());
    }

    public void testUnrecognised() throws IOException
    {
        TestSuiteResult tests = runProcessorAndGetTests(createProcessor(), EXTENSION);
        assertEquals(3, tests.getTotal());
        assertEquals(1, tests.getTotalWithStatus(FAILURE));
        assertEquals(1, tests.getTotalWithStatus(ERROR));
        assertEquals(PASS, tests.findCase("test1").getStatus());
        assertEquals(ERROR, tests.findCase("test2").getStatus());
        assertEquals(FAILURE, tests.findCase("test4").getStatus());
        assertNull(tests.findCase("test3"));
        assertNull(tests.findCase("test5"));
    }

    public void testDetails() throws IOException
    {
        TestSuiteResult tests = runProcessorAndGetTests(createProcessor(false, 3), EXTENSION);
        assertEquals(4, tests.getTotal());
        assertEquals(2, tests.getTotalWithStatus(FAILURE));
        assertEquals("fail 1 details", tests.findCase(" <FAIL1>").getMessage());
        assertEquals("fail 2 details", tests.findCase(" <FAIL2>").getMessage());
    }

    public void testSkipped() throws IOException
    {
        TestSuiteResult tests = runProcessorAndGetTests(createProcessor(), EXTENSION);
        TestCaseResult skippedCase = tests.findCase(" <SKIPPY>");
        assertNotNull(skippedCase);
        assertEquals(TestStatus.SKIPPED, skippedCase.getStatus());
    }

    private RegexTestPostProcessor createProcessor()
    {
        return createProcessor(false, -1);
    }

    private RegexTestPostProcessor createProcessor(boolean autoFail, int detailsGroup)
    {
        RegexTestPostProcessorConfiguration pp = new RegexTestPostProcessorConfiguration();
        pp.setRegex("\\[(.*)\\] .*EDT:([^:]*)(?:\\: (.*))?");
        pp.setStatusGroup(1);
        pp.setNameGroup(2);
        pp.setDetailsGroup(detailsGroup);
        pp.setPassStatus("PASS");
        pp.setFailureStatus("FAIL");

        pp.setAutoFail(autoFail);
        return new RegexTestPostProcessor(pp);
    }
}
