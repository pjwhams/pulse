/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.master.scheduling;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.zutubi.pulse.master.model.persistence.TriggerDao;
import com.zutubi.util.junit.ZutubiTestCase;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.collect.Iterables.find;
import static com.zutubi.pulse.master.scheduling.TriggerState.PAUSED;
import static com.zutubi.pulse.master.scheduling.TriggerState.SCHEDULED;
import static org.mockito.Mockito.*;

public class DefaultSchedulerTest extends ZutubiTestCase
{
    private long nextId = 1;

    private DefaultScheduler scheduler;
    private TriggerDao triggerDao;
    private SchedulerStrategy strategy;
    private List<Trigger> persistedTriggers;

    public void setUp() throws Exception
    {
        super.setUp();

        // add setup code here.
        scheduler = new DefaultScheduler();
        triggerDao = mock(TriggerDao.class);

        persistedTriggers = new LinkedList<Trigger>();
        
        stub(triggerDao.findByNameAndGroup(anyString(), anyString())).toAnswer(new Answer()
        {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                String name = (String)invocationOnMock.getArguments()[0];
                String group = (String)invocationOnMock.getArguments()[1];
                return find(persistedTriggers, new HasNameAndGroupPredicate(name, group), null);
            }
        });
        stub(triggerDao.findByGroup(anyString())).toAnswer(new Answer()
        {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                String group = (String)invocationOnMock.getArguments()[0];
                return Lists.newArrayList(Iterables.filter(persistedTriggers, new HasGroupPredicate(group)));
            }
        });
        stub(triggerDao.findAll()).toReturn(persistedTriggers);

        scheduler.setTriggerDao(triggerDao);

        strategy = mock(SchedulerStrategy.class);
        stub(strategy.canHandle()).toReturn(Arrays.asList(NoopTrigger.TYPE));

        scheduler.register(strategy);
        scheduler.start();
    }

    public void testPersistenceTriggerScheduling() throws SchedulingException
    {
        Trigger trigger = new NoopTrigger("unknown", "group");
        trigger.setTaskClass(NoopTask.class);
        assertFalse(trigger.isScheduled());
        scheduler.schedule(trigger);

        verify(triggerDao, times(1)).save(trigger);
        verify(strategy, times(1)).schedule(trigger);
        assertTrue(trigger.isScheduled());
    }

    public void testTransientTriggerScheduling() throws SchedulingException
    {
        Trigger trigger = new NoopTrigger("unknown", "group");
        trigger.setTaskClass(NoopTask.class);
        trigger.setTransient(true);
        assertFalse(trigger.isScheduled());
        scheduler.schedule(trigger);

        verify(triggerDao, never()).save(trigger);
        verify(strategy, times(1)).schedule(trigger);
        assertTrue(trigger.isScheduled());
    }

    public void testSchedulingTriggerWithExistingName() throws SchedulingException
    {
        schedulePersistentTrigger("name", "group");

        try
        {
            scheduler.schedule(createPersistentTrigger("name", "group"));
            fail();
        }
        catch (SchedulingException e)
        {
            // expected
        }
    }

    public void testSchedulingTransientTriggerWithExistingName() throws SchedulingException
    {
        schedulePersistentTrigger("name", "group");

        try
        {
            scheduler.schedule(createTransientTrigger("name", "group"));
            fail();
        }
        catch (SchedulingException e)
        {
            // expected
        }
    }

    public void testSchedulingTransientTriggersWithSameName() throws SchedulingException
    {
        scheduleTransientTrigger("name", "group");
        try
        {
            scheduler.schedule(createTransientTrigger("name", "group"));
            fail();
        }
        catch (SchedulingException e)
        {
            // expected
        }
    }

    public void testGetTriggers() throws SchedulingException
    {
        schedulePersistentTrigger("name", "persistent");
        scheduleTransientTrigger("name", "transient");

        List<Trigger> triggers = scheduler.getTriggers();
        assertEquals(2, triggers.size());
    }

    public void testPauseAndResumeGroup() throws SchedulingException
    {
        Trigger triggerA = schedulePersistentTrigger("triggerA", "scheduled");
        Trigger triggerB = scheduleTransientTrigger("triggerB", "scheduled");
        Trigger triggerC = scheduleTransientTrigger("triggerC", "notpaused");

        scheduler.pause("scheduled");

        // verify expected triggers are paused with there strategies, and that there states are updated accordingly.
        verify(strategy, times(1)).pause(triggerA);
        verify(strategy, times(1)).pause(triggerB);
        verify(strategy, never()).pause(triggerC);

        assertEquals(PAUSED, triggerA.getState());
        assertEquals(PAUSED, triggerB.getState());
        assertEquals(SCHEDULED, triggerC.getState());

        scheduler.resume("scheduled");

        verify(strategy, times(1)).resume(triggerA);
        verify(strategy, times(1)).resume(triggerB);
        verify(strategy, never()).resume(triggerC);

        assertEquals(SCHEDULED, triggerA.getState());
        assertEquals(SCHEDULED, triggerB.getState());
        assertEquals(SCHEDULED, triggerC.getState());
    }

    /**
     * CIB-1264: renaming paused trigger re-enables it.
     *
     * @throws SchedulingException if an error occurs.
     */
    public void testUpdatePausedTriggerRemainsPaused() throws SchedulingException
    {
        Trigger trigger = schedulePersistentTrigger("a", "b");

        scheduler.pause(trigger);
        assertEquals(PAUSED, trigger.getState());

        scheduler.preUpdate(trigger);
        trigger.setName("b");
        scheduler.postUpdate(trigger);
        assertEquals(PAUSED, trigger.getState());
    }

    public void testUnschedulePersistentTrigger() throws SchedulingException
    {
        Trigger trigger = schedulePersistentTrigger("a", "a");

        scheduler.unschedule(trigger);

        assertEquals(TriggerState.NONE, trigger.getState());
        verify(triggerDao, times(1)).delete(trigger);
        verify(strategy, times(1)).unschedule(trigger);
    }

    public void testUnscheduleTransientTrigger() throws SchedulingException
    {
        Trigger trigger = scheduleTransientTrigger("a", "a");

        scheduler.unschedule(trigger);

        assertEquals(TriggerState.NONE, trigger.getState());
        verify(strategy, times(1)).unschedule(trigger);
        verify(triggerDao, never()).delete(trigger);
    }

    public void testUnknownTriggerType()
    {
        Trigger trigger = new SimpleTrigger("a", "b", 1);
        try
        {
            scheduler.schedule(trigger);
            fail();
        }
        catch (SchedulingException e)
        {
            // noop.
        }
    }

    private Trigger createPersistentTrigger(String name, String group)
    {
        NoopTrigger trigger = new NoopTrigger(name, group);
        trigger.setTaskClass(NoopTask.class);
        return trigger;
    }

    private Trigger schedulePersistentTrigger(String name, String group) throws SchedulingException
    {
        Trigger trigger = createPersistentTrigger(name, group);
        scheduler.schedule(trigger);
        trigger.setId(nextId++);
        persistedTriggers.add(trigger);
        return trigger;
    }

    private Trigger createTransientTrigger(String name, String group)
    {
        NoopTrigger trigger = new NoopTrigger(name, group);
        trigger.setTaskClass(NoopTask.class);
        trigger.setTransient(true);
        return trigger;
    }

    private Trigger scheduleTransientTrigger(String name, String group) throws SchedulingException
    {
        Trigger trigger = createTransientTrigger(name, group);
        scheduler.schedule(trigger);
        return trigger;
    }
}