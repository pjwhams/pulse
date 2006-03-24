package com.cinnamonbob.upgrade;

import com.cinnamonbob.Version;
import com.cinnamonbob.bootstrap.Home;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * <class-comment/>
 */
public class DefaultUpgradeManager implements UpgradeManager
{
    /**
     * The registered upgrade tasks.
     */
    private List<UpgradeTask> upgradeTasks = new LinkedList<UpgradeTask>();

    /**
     * The upgrade context for the current upgrade. This will be null if no upgrade
     * is in progress / prepared.
     */
    private DefaultUpgradeContext currentContext;

    private UpgradeProgressMonitor monitor;

    /**
     * Register an upgrade task with this upgrade manager.
     *
     * @param task to be registered
     */
    public void addTask(UpgradeTask task)
    {
        upgradeTasks.add(task);
    }

    /**
     * Set the full list of upgrade tasks. This new set of tasks will override any existing
     * registered tasks.
     *
     * @param tasks
     */
    public void setTasks(List<UpgradeTask> tasks)
    {
        this.upgradeTasks = tasks;
    }

    /**
     * Determine if an upgrade is required between the specified versions.
     *
     * @param fromVersion
     * @param toVersion
     *
     * @return true if an upgrade is required, false otherwise.
     */
    public boolean isUpgradeRequired(Version fromVersion, Version toVersion)
    {
        if ( fromVersion.compareTo(toVersion) < 0 )
        {
            List<UpgradeTask> requiredTasks = determineRequiredUpgradeTasks(fromVersion, toVersion);
            if (requiredTasks.size() > 0)
            {
                return true;
            }
        }
        return false;
    }

    public List<UpgradeTask> previewUpgrade(Version fromVersion, Version toVersion)
    {
        return determineRequiredUpgradeTasks(fromVersion, toVersion);
    }

    /**
     * Determine which upgrade tasks need to be executed during an upgrade between the
     * indicated versions.
     *
     * @param fromVersion specifies the lower version and is not included in the determination.
     * @param toVersion specified the upper version and is included in the determination.
     *
     * @return a list of upgrade tasks that are required.
     */
    protected List<UpgradeTask> determineRequiredUpgradeTasks(Version fromVersion, Version toVersion)
    {
        List<UpgradeTask> requiredTasks = new LinkedList<UpgradeTask>();

        int from = Integer.parseInt(fromVersion.getBuildNumber());
        int to = Integer.parseInt(toVersion.getBuildNumber());

        for (UpgradeTask task : upgradeTasks)
        {
            if (from < task.getBuildNumber() && task.getBuildNumber() <= to)
            {
                requiredTasks.add(task);
            }
        }
        Collections.sort(requiredTasks, new UpgradeTaskComparator());
        return requiredTasks;
    }

    /**
     * Check if the specified home directory requires an upgrade to be used with the
     * current installation.
     *
     * @param home
     *
     * @return true if an upgrade is required, false otherwise.
     */
    public boolean isUpgradeRequired(Home home)
    {
        checkHome(home);

        Version from = home.getHomeVersion();
        Version to = Version.getVersion();

        return isUpgradeRequired(from, to);
    }

    /**
     * Prepare to upgrade the specified home directory. Prepare must be called before
     * the upgrade can be executed and before any specific details about the upgrade
     * are available.
     *
     * @param home
     */
    public void prepareUpgrade(Home home)
    {
        checkHome(home);

        // ensure that
        // a) upgrade is not in progress.

        Version from = home.getHomeVersion();
        Version to = Version.getVersion();

        List<UpgradeTask> tasks = determineRequiredUpgradeTasks(from, to);

        // copy the tasks...

        currentContext = new DefaultUpgradeContext(from, to);
        currentContext.setTasks(tasks);

        monitor = new UpgradeProgressMonitor();
    }

    /**
     * Retrieve the list of upgrade tasks that will be executed when the next
     * upgrade is triggered.
     *
     * @return a list of upgrade tasks.
     */
    public List<UpgradeTask> previewUpgrade()
    {
        if (currentContext == null)
        {
            throw new IllegalArgumentException("no upgrade has been prepared.");
        }

        return currentContext.getTasks();
    }

    /**
     * Start executing the upgrade.
     *
     */
    public void executeUpgrade()
    {
        List<UpgradeTask> tasksToExecute = currentContext.getTasks();
        UpgradeContext context = currentContext;

        monitor.setTasks(tasksToExecute);
        monitor.start();

        boolean abort = false;
        for (UpgradeTask task : tasksToExecute)
        {
            try
            {
                if (!abort)
                {
                    monitor.start(task);
                    task.execute(context);
                    monitor.complete(task);
                }
                else
                {
                    monitor.aborted(task);
                }
            }
            catch (UpgradeException e)
            {
                monitor.failed(task);
                if (task.haltOnFailure())
                {
                    abort = true;
                }
            }
        }
        monitor.stop();
    }

    public UpgradeProgressMonitor getUpgradeMonitor()
    {
        return monitor;
    }

    /**
     * Check that the specified home instance if a valid instance to be working
     * with.
     *
     * @param home
     *
     * @throws IllegalArgumentException if the home instance is not valid.
     */
    private void checkHome(Home home)
    {
        if (!home.isInitialised())
        {
            throw new IllegalArgumentException();
        }
    }
}
