package com.zutubi.pulse.web.upgrade;

import com.zutubi.pulse.master.bootstrap.SetupManager;

/**
 * <class-comment/>
 */
public class PostUpgradeAction extends UpgradeActionSupport
{
    private SetupManager setupManager;

    public String execute() throws Exception
    {
        setupManager.requestUpgradeComplete(true);
        return SUCCESS;
    }

    public void setSetupManager(SetupManager setupManager)
    {
        this.setupManager = setupManager;
    }
}
