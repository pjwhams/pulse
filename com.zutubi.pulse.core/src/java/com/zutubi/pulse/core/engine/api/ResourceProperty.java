package com.zutubi.pulse.core.engine.api;

public class ResourceProperty
{
    private String name;
    private String value;
    private boolean addToEnvironment = false;
    private boolean addToPath = false;
    private boolean resolveVariables = false;

    public ResourceProperty()
    {
    }

    public ResourceProperty(String name, String value)
    {
        this(name, value, false, false, false);
    }

    public ResourceProperty(String name, String value, boolean addToEnvironment, boolean addToPath, boolean resolveVariables)
    {
        this.name = name;
        this.value = value;
        this.addToEnvironment = addToEnvironment;
        this.addToPath = addToPath;
        this.resolveVariables = resolveVariables;
    }

    public ResourceProperty copy()
    {
        return new ResourceProperty(getName(), value, addToEnvironment, addToPath, resolveVariables);
    }

    public String getName()
    {
        return name;
    }

    public String getValue()
    {
        return value;
    }

    public boolean getAddToEnvironment()
    {
        return addToEnvironment;
    }

    public boolean getAddToPath()
    {
        return addToPath;
    }

    public boolean getResolveVariables()
    {
        return resolveVariables;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

    public void setAddToEnvironment(boolean addToEnvironment)
    {
        this.addToEnvironment = addToEnvironment;
    }

    public void setAddToPath(boolean addToPath)
    {
        this.addToPath = addToPath;
    }

    public void setResolveVariables(boolean resolveVariables)
    {
        this.resolveVariables = resolveVariables;
    }

    @Override
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

        ResourceProperty that = (ResourceProperty) o;

        if (addToEnvironment != that.addToEnvironment)
        {
            return false;
        }
        if (addToPath != that.addToPath)
        {
            return false;
        }
        if (resolveVariables != that.resolveVariables)
        {
            return false;
        }
        if (!name.equals(that.name))
        {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = name.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (addToEnvironment ? 1 : 0);
        result = 31 * result + (addToPath ? 1 : 0);
        result = 31 * result + (resolveVariables ? 1 : 0);
        return result;
    }
}
