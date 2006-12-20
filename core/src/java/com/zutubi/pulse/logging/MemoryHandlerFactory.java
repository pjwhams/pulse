package com.zutubi.pulse.logging;

import java.util.Properties;
import java.util.logging.MemoryHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import java.util.logging.Handler;

/**
 * <class-comment/>
 */
public class MemoryHandlerFactory implements HandlerFactory
{
    public MemoryHandler createHandler(String name, Properties config)
    {
        int size = LogUtils.getInt(config, name + ".size", 1000);
        if (size <= 0) {
            size = 1000;
        }

        Handler target;
        try
        {
            String clsName = config.getProperty(name + ".target");
            Class cls = Class.forName(clsName);
            target = (Handler)cls.newInstance();
        }
        catch (Exception e)
        {
            return null;
        }

        Level pushLevel = LogUtils.getLevel(config, name+".push", Level.SEVERE);

        MemoryHandler handler = new MemoryHandler(target, size, pushLevel);

        handler.setLevel(LogUtils.getLevel(config, name + ".level", Level.ALL));
        handler.setFilter(LogUtils.getFilter(config, name +".filter", null));
        handler.setFormatter(LogUtils.getFormatter(config, name +".formatter", new SimpleFormatter()));

        return handler;
    }
}
