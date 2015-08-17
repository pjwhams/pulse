package com.zutubi.pulse.master.tove.table;

import com.zutubi.tove.ConventionSupport;
import com.zutubi.tove.type.CompositeType;
import com.zutubi.tove.type.TypeProperty;
import com.zutubi.util.EnumUtils;
import com.zutubi.util.bean.BeanUtils;
import com.zutubi.util.bean.ObjectFactory;
import com.zutubi.util.logging.Logger;

import java.lang.reflect.Method;

/**
 * A wrapper object that provides access to formatted property values.
 *
 * FIXME kendo replaced in new UI
 */
public class FormattingWrapper
{
    private static final Logger LOG = Logger.getLogger(FormattingWrapper.class);

    private ObjectFactory objectFactory;

    /**
     * The instance being wrapped.
     */
    private Object instance;

    /**
     * The type definition for the wrapped instance.
     */
    private CompositeType type;

    public FormattingWrapper(Object instance, CompositeType type)
    {
        this.instance = instance;
        this.type = type;

        if (!type.getClazz().isAssignableFrom(instance.getClass()))
        {
            throw new IllegalArgumentException("Instance: " + instance + " not of the expected type: " + type.getClazz());
        }
    }

    @SuppressWarnings({"unchecked"})
    public Object get(String name) throws Exception
    {
        try
        {
            Class<?> formatter = ConventionSupport.getFormatter(type);
            if (formatter != null)
            {
                Object formatterInstance = objectFactory.buildBean(formatter);

                String methodName = getGetterMethodName(name);
                Method getter;
                try
                {
                    getter = formatter.getMethod(methodName, instance.getClass());
                    return getter.invoke(formatterInstance, instance);
                }
                catch (NoSuchMethodException e)
                {
                    // noop
                }

                try
                {
                    getter = formatter.getMethod(methodName, type.getClazz());
                    return getter.invoke(formatterInstance, instance);
                }
                catch (NoSuchMethodException e)
                {
                    // noop
                }
            }
        }
        catch (Exception e)
        {
            LOG.warning(e);
        }

        Object fieldValue = getFieldValue(name);
        if (fieldValue != null && fieldValue.getClass().isEnum())
        {
            return EnumUtils.toPrettyString((Enum) fieldValue);
        }
        
        return fieldValue;
    }

    private Object getFieldValue(String name) throws Exception
    {
        TypeProperty property = type.getProperty(name);
        if (property != null)
        {
            return property.getValue(instance);
        }
        else
        {
            try
            {
                return BeanUtils.getProperty(name, instance);
            }
            catch (Exception e)
            {
                LOG.warning(e);
            }
            return null;
        }
    }

    private String getGetterMethodName(String name)
    {
        return "get" + name.substring(0,1).toUpperCase() + name.substring(1);
    }

    public void setObjectFactory(ObjectFactory objectFactory)
    {
        this.objectFactory = objectFactory;
    }
}
