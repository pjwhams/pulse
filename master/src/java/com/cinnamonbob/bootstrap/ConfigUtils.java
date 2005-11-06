package com.cinnamonbob.bootstrap;

/**
 * 
 *
 */
public class ConfigUtils
{
    public static final String BEAN_NAME = "configurationManager";

    public static final ConfigurationManager getManager()
    {
        return (ConfigurationManager) ComponentContext.getBean(BEAN_NAME);
    }
}
