package com.zutubi.pulse.command;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.bootstrap.*;
import com.zutubi.pulse.bootstrap.conf.EnvConfig;
import com.zutubi.pulse.upgrade.tasks.MutableConfiguration;
import org.apache.commons.cli.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The abstract base command for commands used to import/export data.
 */
public abstract class DataCommand implements Command
{
    private static final String ENV_PULSE_CONFIG = "PULSE_CONFIG";

    /**
     * The pulse data directory
     */
    private String pulseData = null;

    /**
     * The pulse configuration file.
     */
    private String pulseConfig;

    protected DataSource dataSource;
    protected MasterConfigurationManager configurationManager;
    protected DatabaseConfig databaseConfig;
    protected MutableConfiguration configuration;

    /**
     * The path to the configuration file that is used to configure the pulse configuration details.
     *
     * @param path
     */
    public void setConfig(String path)
    {
        this.pulseConfig = path;
    }

    /**
     * The path to the pulse data directory
     *
     * @param data directory
     */
    public void setData(String data)
    {
        this.pulseData = data;
    }

    public int execute(BootContext context) throws ParseException, IOException
    {
        CommandLine commandLine = parse(context.getCommandArgv());
        updateSystemProperties();

        SystemBootstrapManager sbm = new SystemBootstrapManager();
        sbm.loadBootstrapContext();

        configurationManager = (MasterConfigurationManager) ComponentContext.getBean("configurationManager");
        if (!checkConfigFile())
        {
            return 2;
        }

        ComponentContext.addClassPathContextDefinitions("classpath:/com/zutubi/pulse/bootstrap/context/databaseContext.xml");
        ComponentContext.addClassPathContextDefinitions("classpath:/com/zutubi/pulse/bootstrap/context/hibernateMappingsContext.xml");

        dataSource = (DataSource) ComponentContext.getBean("dataSource");

        configuration = new MutableConfiguration();

        List<String> mappings = (List<String>) ComponentContext.getBean("hibernateMappings");
        for (String mapping : mappings)
        {
            Resource resource = new ClassPathResource(mapping);
            configuration.addInputStream(resource.getInputStream());
        }

        databaseConfig = configurationManager.getDatabaseConfig();
        configuration.setProperties(databaseConfig.getHibernateProperties());

        DefaultSetupManager.printConsoleMessage("Using database configuration '%s'", configurationManager.getDatabaseConfigFile());
        DefaultSetupManager.printConsoleMessage("Using database '%s'", databaseConfig.getUrl());

        return doExecute(commandLine);
   }

    private void updateSystemProperties()
    {
        if (TextUtils.stringSet(pulseData))
        {
            System.setProperty(SystemConfiguration.PULSE_DATA, pulseData);
        }

        if (TextUtils.stringSet(pulseConfig))
        {
            System.setProperty(EnvConfig.PULSE_CONFIG, pulseConfig);
        }
        else if (TextUtils.stringSet(System.getenv(ENV_PULSE_CONFIG)))
        {
            System.setProperty(EnvConfig.PULSE_CONFIG, System.getenv(ENV_PULSE_CONFIG));
        }
    }

    private boolean checkConfigFile()
    {
        EnvConfig envConfig = configurationManager.getEnvConfig();
        String configFileName = envConfig.getPulseConfig();
        if(TextUtils.stringSet(configFileName))
        {
            File configFile = new File(configFileName);
            DefaultSetupManager.printConsoleMessage("Using config file '%s'", configFile.getAbsolutePath());
            if(!configFile.exists())
            {
                System.err.println("Specified config file '" + configFileName + "' does not exist");
                return false;
            }
        }
        else
        {
            configFileName = envConfig.getDefaultPulseConfig(MasterConfigurationManager.CONFIG_DIR);
            File configFile = new File(configFileName);
            DefaultSetupManager.printConsoleMessage("No config file specified, using default '%s'", configFile.getAbsolutePath());
            if(!configFile.exists())
            {
                System.err.println("Default config file '" + configFileName + "' does not exist");
                return false;
            }
        }

        return true;
    }

    public Map<String, String> getOptions()
    {
        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("-f [--config] file", "specify an alternate config file");
        options.put("-d [--data] dir", "use the specified directory for all pulse data");
        return options;
    }

    public boolean isDefault()
    {
        return false;
    }

    /**
     * Data command implementations should implement their custom
     * functionality in this method. When this method is invoked, the
     * dataSource will be available.
     */
    public abstract int doExecute(CommandLine commandLine) throws IOException, ParseException;

    @SuppressWarnings({ "AccessStaticViaInstance" })
    protected Options getSharedOptions()
    {
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("config")
                .hasArg()
                .create('f'));
        options.addOption(OptionBuilder.withLongOpt("data")
                .hasArg()
                .create('d'));
        return options;
    }

    protected void processSharedOptions(CommandLine commandLine)
    {
        if (commandLine.hasOption('f'))
        {
            setConfig(commandLine.getOptionValue('f'));
        }
        if (commandLine.hasOption('d'))
        {
            setData(commandLine.getOptionValue('d'));
        }
    }

    public CommandLine parse(String... argv) throws ParseException
    {
        Options options = getSharedOptions();
        
        CommandLineParser parser = new PosixParser();
        CommandLine commandLine = parser.parse(options, argv, false);

        processSharedOptions(commandLine);
        return commandLine;
    }
}
