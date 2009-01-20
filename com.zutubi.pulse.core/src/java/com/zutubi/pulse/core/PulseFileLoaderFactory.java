package com.zutubi.pulse.core;

import com.zutubi.pulse.core.engine.api.Property;
import com.zutubi.pulse.core.engine.RecipeConfiguration;
import com.zutubi.pulse.core.engine.ProjectRecipesConfiguration;
import com.zutubi.pulse.core.api.PulseRuntimeException;
import com.zutubi.tove.type.CompositeType;
import com.zutubi.tove.type.TypeRegistry;
import com.zutubi.tove.type.TypeException;
import com.zutubi.util.bean.ObjectFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A factory for creating PulseFileLoader objects that are aware of the
 * current plugins.
 */
public class PulseFileLoaderFactory
{
    private Map<String, CompositeType> types = new HashMap<String, CompositeType>();
    private ObjectFactory objectFactory;
    private TypeRegistry typeRegistry;

    public void init()
    {
        try
        {
            typeRegistry.register(ProjectRecipesConfiguration.class);
        }
        catch (TypeException e)
        {
// FIXME
            e.printStackTrace();
        }

        register("property", Property.class);
        register("recipe", RecipeConfiguration.class);
        // FIXME loader scrap this?
//        register("version", Version.class);
    }

    public PulseFileLoader createLoader()
    {
        PulseFileLoader loader = objectFactory.buildBean(PulseFileLoader.class);
        for(Map.Entry<String, CompositeType> entry: types.entrySet())
        {
            loader.register(entry.getKey(), entry.getValue());
        }
        return loader;
    }

    public void register(String name, Class clazz)
    {
        CompositeType type = typeRegistry.getType(clazz);
        if (type == null)
        {
            try
            {
                // FIXME loader actually the type should already be registered
                typeRegistry.register(clazz);
            }
            catch (TypeException e)
            {
                throw new PulseRuntimeException("Unable to register type: " + e.getMessage(), e);
            }
        }

        types.put(name, type);
    }

    public void unregister(String name)
    {
        types.remove(name);
    }

    public void setObjectFactory(ObjectFactory objectFactory)
    {
        this.objectFactory = objectFactory;
    }

    public void setTypeRegistry(TypeRegistry typeRegistry)
    {
        this.typeRegistry = typeRegistry;
    }
}
