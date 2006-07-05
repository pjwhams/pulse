package com.zutubi.pulse;

import com.zutubi.pulse.core.BuildRevision;
import com.zutubi.pulse.events.build.BuildRequestEvent;
import com.zutubi.pulse.model.BuildReason;
import com.zutubi.pulse.model.Project;
import com.zutubi.pulse.test.PulseTestCase;

/**
 */
public class ProjectQueueTest extends PulseTestCase
{
    private ProjectQueue queue = new ProjectQueue();
    private Project p1;
    private Project p2;

    protected void setUp() throws Exception
    {
        super.setUp();

        p1 = new Project("p1", "test project 1");
        p2 = new Project("p2", "test project 2");
    }

    public void testSimpleQueue()
    {
        assertTrue(queue.buildRequested(createEvent(p1, "spec1")));
        assertNull(queue.buildCompleted(p1));
    }

    public void testQueueTwice()
    {
        assertTrue(queue.buildRequested(createEvent(p1, "spec1")));
        assertNull(queue.buildCompleted(p1));
        assertTrue(queue.buildRequested(createEvent(p1, "spec1")));
        assertNull(queue.buildCompleted(p1));
    }

    public void testSimpleWait()
    {
        assertTrue(queue.buildRequested(createEvent(p1, "spec1")));
        BuildRequestEvent request2 = createEvent(p1, "spec2");
        assertFalse(queue.buildRequested(request2));
        assertEquals(request2, queue.buildCompleted(p1));
        assertNull(queue.buildCompleted(p1));
    }

    public void testWaitSameSpec()
    {
        assertTrue(queue.buildRequested(createEvent(p1, "spec1")));
        BuildRequestEvent request2 = createEvent(p1, "spec1");
        assertFalse(queue.buildRequested(request2));
        assertEquals(request2, queue.buildCompleted(p1));
        assertNull(queue.buildCompleted(p1));
    }

    public void testMultiProjects()
    {
        assertTrue(queue.buildRequested(createEvent(p1, "spec1-1")));
        assertTrue(queue.buildRequested(createEvent(p2, "spec2-1")));
        assertNull(queue.buildCompleted(p1));
        assertNull(queue.buildCompleted(p2));
    }

    public void testQueueMultipleSpecs()
    {
        assertTrue(queue.buildRequested(createEvent(p1, "spec1")));
        BuildRequestEvent request2 = createEvent(p1, "spec2");
        assertFalse(queue.buildRequested(request2));
        BuildRequestEvent request3 = createEvent(p1, "spec3");
        assertFalse(queue.buildRequested(request2));
        assertFalse(queue.buildRequested(request3));
        assertEquals(request2, queue.buildCompleted(p1));
        assertEquals(request3, queue.buildCompleted(p1));
        assertNull(queue.buildCompleted(p1));
    }

    public void testQueueSameSpecTwice()
    {
        assertTrue(queue.buildRequested(createEvent(p1, "spec1")));
        BuildRequestEvent request2 = createEvent(p1, "spec2");
        assertFalse(queue.buildRequested(request2));
        BuildRequestEvent request3 = createEvent(p1, "spec2");
        assertFalse(queue.buildRequested(request2));
        assertFalse(queue.buildRequested(request3));
        assertEquals(request2, queue.buildCompleted(p1));
        assertNull(queue.buildCompleted(p1));
    }

    private BuildRequestEvent createEvent(Project project, String spec)
    {
        return new BuildRequestEvent(this, new MockBuildReason(), project, spec, new BuildRevision());
    }

    private class MockBuildReason implements BuildReason
    {
        public String getSummary()
        {
            return "mock";
        }
    }
}
