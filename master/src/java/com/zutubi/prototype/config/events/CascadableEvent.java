package com.zutubi.prototype.config.events;

import com.zutubi.prototype.config.ConfigurationTemplateManager;
import com.zutubi.pulse.core.config.Configuration;

/**
 */
public abstract class CascadableEvent extends ConfigurationEvent
{
    private boolean cascaded;

    protected CascadableEvent(ConfigurationTemplateManager source, Configuration instance, boolean cascaded)
    {
        super(source, instance);
        this.cascaded = cascaded;
    }

    public boolean isCascaded()
    {
        return cascaded;
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        if (!super.equals(o))
        {
            return false;
        }

        CascadableEvent event = (CascadableEvent) o;
        return cascaded == event.cascaded;
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (cascaded ? 1 : 0);
        return result;
    }
}
