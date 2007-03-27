package com.zutubi.pulse.web.upgrade;

import com.zutubi.pulse.upgrade.UpgradeProgressMonitor;

/**
 * <class-comment/>
 */
public class UpgradeExecuteAction extends UpgradeActionSupport
{
    public UpgradeProgressMonitor getMonitor()
    {
        return upgradeManager.getUpgradeMonitor();
    }

    public String execute()
    {
        // check if the upgrade is in progress... if a user refreshes the browser on the
        // execute action, we do not want the upgrade starting again.
        UpgradeProgressMonitor progress = getMonitor();

        if (!progress.isStarted())
        {
            upgradeManager.executeUpgrade();
        }
        
        // CIB-1028: Initial upgrade page status shows 100%
        // Wait for the upgrade process to start before we return. Why? To prevent the possibility
        // of the upgrade status page being rendered BEFORE the upgrade system is properly initialised.
        while (!progress.isStarted())
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
                // noop.
            }
        }

        // go to the progress monitor screen.
        return SUCCESS;
    }
}
