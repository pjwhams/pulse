package com.cinnamonbob.bootstrap;

import com.cinnamonbob.core.ObjectFactory;
import com.cinnamonbob.events.EventManager;
import com.cinnamonbob.events.system.SystemStartedEvent;
import com.cinnamonbob.util.logging.Logger;

import java.util.LinkedList;
import java.util.List;

/**
 * 
 *
 */
public class DefaultStartupManager implements StartupManager
{
    private static final Logger LOG = Logger.getLogger(DefaultStartupManager.class);

    private List<String> setupContexts;
    private List<String> systemContexts;
    private List<String> startupRunnables = new LinkedList<String>();

    private boolean systemStarted;
    private long startTime;

    private ObjectFactory objectFactory;

    public void init() throws StartupException
    {
        if (isSystemStarted())
        {
            throw new StartupException("Attempt to start system when it has already started.");
        }

        try
        {
            ComponentContext.addClassPathContextDefinitions(setupContexts.toArray(new String[setupContexts.size()]));

            // initial Web UI.
            WebUIState.startStarting();

            SetupManager setupManager = (SetupManager) ComponentContext.getBean("setupManager");
            if (!setupManager.isSetup())
            {
                setupManager.setup();
                return;
            }

            startApplication();
        }
        catch (Exception e)
        {
            throw new StartupException(e);
        }
    }

    public void startApplication()
    {
        ComponentContext.addClassPathContextDefinitions(systemContexts.toArray(new String[systemContexts.size()]));

        for (String name : startupRunnables)
        {
            try
            {
                Runnable instance = objectFactory.buildBean(name);
                instance.run();
            }
            catch (Exception e)
            {
                LOG.warning("Failed to run startup task "+name+". Reason: " + e.getMessage(), e);
            }
        }

        setSystemStarted(true);

        // allow people to access the site.
        WebUIState.startMain();

        EventManager eventManager = (EventManager) ComponentContext.getBean("eventManager");
        eventManager.publish(new SystemStartedEvent(this));
    }

    private void setSystemStarted(boolean b)
    {
        systemStarted = b;
        if (systemStarted)
        {
            startTime = System.currentTimeMillis();
        }
    }

    public boolean isSystemStarted()
    {
        return systemStarted;
    }

    public void setSystemContexts(List<String> contexts)
    {
        this.systemContexts = contexts;
    }

    public void setSetupContexts(List<String> setupContexts)
    {
        this.setupContexts = setupContexts;
    }

    public void setStartupRunnables(List<String> classes)
    {
        this.startupRunnables = classes;
    }

    public long getUptime()
    {
        return System.currentTimeMillis() - startTime;
    }

    public long getStartTime()
    {
        return startTime;
    }

    public void setObjectFactory(ObjectFactory objectFactory)
    {
        this.objectFactory = objectFactory;
    }
}
