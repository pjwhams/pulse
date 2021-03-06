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

package com.zutubi.pulse.servercore.agent;

import java.util.Properties;

/**
 * A single message that an agent needs to process to synchronise.  These
 * messages are processed just before an agent becomes idle: i.e. when it
 * comes online, and just after a build completes.
 */
public class SynchronisationMessage
{
    private long id;
    private String typeName;
    private Properties arguments = new Properties();

    /**
     * For hibernate.
     *
     * @see #SynchronisationMessage(String, java.util.Properties)
     */
    public SynchronisationMessage()
    {
    }

    /**
     * Creates a new synchronisation message representing a task of the given
     * type with the given arguments.  These details are used to convert this
     * message into an executable task on the agent side.
     *
     * @param typeName  name of the type of task this message represents
     * @param arguments arguments for the task (name-value string pairs)
     */
    public SynchronisationMessage(String typeName, Properties arguments)
    {
        this.typeName = typeName;
        this.arguments = arguments;
    }

    /**
     * @return a unique id for this message
     */
    public long getId()
    {
        return id;
    }

    /**
     * Sets the identifier for this message.
     * 
     * @param id a unique identifier for the message
     */
    public void setId(long id)
    {
        this.id = id;
    }

    /**
     * @return the type of task this message represents
     */
    public SynchronisationTask.Type getType()
    {
        return SynchronisationTask.Type.valueOf(typeName);
    }

    public void setType(SynchronisationTask.Type type)
    {
        this.typeName = type.name();
    }

    public String getTypeName()
    {
        return typeName;
    }

    private void setTypeName(String typeName)
    {
        this.typeName = typeName;
    }

    /**
     * @return arguments for the task (name-value string pairs)
     */
    public Properties getArguments()
    {
        return arguments;
    }

    private void setArguments(Properties arguments)
    {
        this.arguments = arguments;
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

        SynchronisationMessage that = (SynchronisationMessage) o;

        if (!arguments.equals(that.arguments))
        {
            return false;
        }
        if (!typeName.equals(that.typeName))
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = typeName.hashCode();
        result = 31 * result + arguments.hashCode();
        return result;
    }
}
