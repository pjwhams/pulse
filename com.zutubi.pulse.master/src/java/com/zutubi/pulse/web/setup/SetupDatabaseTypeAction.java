package com.zutubi.pulse.web.setup;

import com.zutubi.pulse.database.DriverRegistry;
import com.zutubi.pulse.master.bootstrap.Data;
import com.zutubi.pulse.master.bootstrap.MasterConfigurationManager;
import com.zutubi.pulse.master.bootstrap.SetupManager;
import com.zutubi.pulse.tove.config.setup.SetupDatabaseTypeConfiguration;
import com.zutubi.tove.webwork.TransientAction;
import com.zutubi.util.io.IOUtils;

import java.io.File;
import java.util.Properties;

/**
 */
public class SetupDatabaseTypeAction extends TransientAction<SetupDatabaseTypeConfiguration>
{
    private MasterConfigurationManager configurationManager;
    private SetupManager setupManager;

    public SetupDatabaseTypeAction()
    {
        super("init/databaseType");
    }

    protected SetupDatabaseTypeConfiguration initialise() throws Exception
    {
        return new SetupDatabaseTypeConfiguration();
    }

    protected String complete(SetupDatabaseTypeConfiguration instance) throws Exception
    {
        Data data = configurationManager.getData();
        if (!instance.getType().isEmbedded())
        {
            DriverRegistry registry = configurationManager.getDriverRegistry();

            if (instance.getDriverFile() != null)
            {
                File driverFile = new File(instance.getDriverFile());
                registry.register(instance.getType().getJDBCClassName(instance), driverFile);
            }
        }

        File databaseConfig = new File(data.getUserConfigRoot(), "database.properties");
        Properties p = instance.getDatabaseProperties();

        IOUtils.write(p, databaseConfig, "Generated by Pulse setup wizard");

        setupManager.requestDbComplete();

        return SUCCESS;
    }

    public void setConfigurationManager(MasterConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }

    public void setSetupManager(SetupManager setupManager)
    {
        this.setupManager = setupManager;
    }
}
