package com.zutubi.prototype.config;

import com.zutubi.prototype.type.CompositeType;
import com.zutubi.prototype.type.TypeException;
import com.zutubi.prototype.type.TypeRegistry;
import com.zutubi.pulse.plugins.AbstractExtensionManager;
import com.zutubi.util.logging.Logger;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;

/**
 * An extension point manager that handles the registration of all
 * configuration types.  This manager is initialised earlier than most
 * extension managers to ensure the types are available.
 */
public class ConfigurationExtensionManager extends AbstractExtensionManager
{
    private static final Logger LOG = Logger.getLogger(ConfigurationExtensionManager.class);
    
    private ConfigurationRegistry configurationRegistry;
    private TypeRegistry typeRegistry;

    public void init()
    {
        // Don't register with plugin manager (i.e. don't call super) as we
        // take care of our own initialisation.
        initialiseExtensions();
    }

    protected String getExtensionPointId()
    {
        return "com.zutubi.pulse.core.config";
    }

    @SuppressWarnings({ "unchecked" })
    protected void handleConfigurationElement(IExtension extension, IExtensionTracker tracker, IConfigurationElement config)
    {
        String className = config.getAttribute("class");
        String extendedSymbolicName = config.getAttribute("extends");

        Class clazz = loadClass(extension, className);
        if(clazz != null)
        {
            try
            {
                if(extendedSymbolicName != null)
                {
                    CompositeType extendedType = typeRegistry.getType(extendedSymbolicName);
                    if(extendedType == null)
                    {
                        String message = "Failed to register config class '" + clazz.getName() + "': extended symbolic name '" + extendedSymbolicName + "' is not recongnised";
                        LOG.warning(message);
                        handleExtensionError(extension, message);
                    }
                    else
                    {
                        configurationRegistry.registerExtension(extendedType.getClazz(), clazz);
                    }
                }
                else
                {
                    configurationRegistry.registerConfigurationType(clazz);
                }
            }
            catch (TypeException e)
            {
                LOG.warning(e);
                handleExtensionError(extension, e);
            }
        }
    }

    public void removeExtension(IExtension iExtension, Object[] objects)
    {
        // Do nothing.
    }


    public void setConfigurationRegistry(ConfigurationRegistry configurationRegistry)
    {
        this.configurationRegistry = configurationRegistry;
    }

    public void setTypeRegistry(TypeRegistry typeRegistry)
    {
        this.typeRegistry = typeRegistry;
    }
}
