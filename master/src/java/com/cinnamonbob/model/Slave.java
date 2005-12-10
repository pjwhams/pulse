package com.cinnamonbob.model;

import com.cinnamonbob.core.model.Entity;

import java.text.DateFormat;
import java.util.Date;

/**
 * Represents a slave server that builds may be farmed out to.
 */
public class Slave extends Entity
{
    public enum Status
    {
        UNKNOWN,
        ONLINE,
        OFFLINE
    }

    private String name;
    private String host;
    private long lastPingTime = 0;
    private Status status = Status.UNKNOWN;

    public Slave()
    {

    }

    public Slave(String name, String host)
    {
        this.name = name;
        this.host = host;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public Status getStatus()
    {
        return status;
    }

    public String getStatusName()
    {
        return status.name();
    }

    private void setStatusName(String name)
    {
        status = Status.valueOf(name);
    }

    public long getLastPingTime()
    {
        return lastPingTime;
    }

    public boolean hasBeenPinged()
    {
        return lastPingTime != 0;
    }

    public String getPrettyPingTime()
    {
        if(hasBeenPinged())
        {
            return DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date(lastPingTime));
        }
        else
        {
            return "<never>";
        }
    }

    public double getSecondsSincePing()
    {
        return (System.currentTimeMillis() - lastPingTime) / 1000.0;
    }

    private void setLastPingTime(long time)
    {
        lastPingTime = time;
    }

    public void lastPing(long time, boolean succeeded)
    {
        this.lastPingTime = time;
        if (succeeded)
        {
            this.status = Status.ONLINE;
        }
        else
        {
            this.status = Status.OFFLINE;
        }
    }
}
