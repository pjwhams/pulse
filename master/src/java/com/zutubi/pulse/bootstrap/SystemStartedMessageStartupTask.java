package com.zutubi.pulse.bootstrap;

import com.zutubi.prototype.config.ConfigurationProvider;
import com.zutubi.pulse.prototype.config.admin.GeneralAdminConfiguration;

/**
 */
public class SystemStartedMessageStartupTask implements StartupTask
{
    private MasterConfigurationManager configurationManager;
    private ConfigurationProvider configurationProvider;

    public void execute()
    {
        // let the user know that the system is now up and running.
        GeneralAdminConfiguration adminConfig = configurationProvider.get(GeneralAdminConfiguration.class);
        SystemConfiguration sysConfig = configurationManager.getSystemConfig();

        //TODO: I18N this message.
        String str = "The server is now available on port %s at context path '%s' [base URL configured as: %s]";
        String msg = String.format(str, sysConfig.getServerPort(), sysConfig.getContextPath(), adminConfig.getBaseUrl());
        System.err.println(msg);
    }

    public boolean haltOnFailure()
    {
        return false;
    }

    public void setConfigurationManager(MasterConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }

    public void setConfigurationProvider(ConfigurationProvider configurationProvider)
    {
        this.configurationProvider = configurationProvider;
    }
}
