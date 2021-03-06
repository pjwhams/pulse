/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.util.config;

/**
 * A wrapper around a delegate config instance that provides some typing support.
 */
public class ConfigSupport implements Config
{
    protected Config delegate;

    public ConfigSupport(Config config)
    {
        this.delegate = config;
    }

    public String getProperty(String key)
    {
        return delegate.getProperty(key);
    }

    public boolean hasProperty(String key)
    {
        return delegate.hasProperty(key);
    }

    public void removeProperty(String key)
    {
        delegate.removeProperty(key);
    }

    public String getProperty(String key, String defaultValue)
    {
        if(hasProperty(key))
        {
            return getProperty(key);
        }
        return defaultValue;
    }

    public void setProperty(String key, String value)
    {
        if (value != null)
        {
            delegate.setProperty(key, value);
        }
        else
        {
            delegate.removeProperty(key);
        }
    }

    public boolean isWritable()
    {
        return delegate.isWritable();
    }

    public Integer getInteger(String key)
    {
        return getInteger(key, null);
    }

    public Integer getInteger(String key, Integer defaultValue)
    {
        if (hasProperty(key))
        {
            try
            {
                return Integer.valueOf(getProperty(key));
            }
            catch (NumberFormatException e)
            {
                // Fall through to default
            }
        }
        
        return defaultValue;
    }

    public void setInteger(String key, Integer value)
    {
        if (value != null)
        {
            setProperty(key, Integer.toString(value));
        }
        else
        {
            removeProperty(key);
        }
    }

    public Long getLong(String key)
    {
        if (hasProperty(key))
        {
            return Long.valueOf(getProperty(key));
        }
        return null;
    }

    public Long getLong(String key, Long defaultValue)
    {
        if (hasProperty(key))
        {
            try
            {
                return Long.valueOf(getProperty(key));
            }
            catch (NumberFormatException e)
            {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    public void setLong(String key, Long value)
    {
        if (value != null)
        {
            setProperty(key, value.toString());
        }
        else
        {
            removeProperty(key);
        }
    }

    public Boolean getBooleanProperty(String key, Boolean defaultValue)
    {
        if (hasProperty(key))
        {
            return Boolean.valueOf(getProperty(key));
        }
        return defaultValue;
    }

    public void setBooleanProperty(String key, Boolean val)
    {
        if (val != null)
        {
            setProperty(key, val.toString());
        }
        else
        {
            removeProperty(key);
        }
    }
}
