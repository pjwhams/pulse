package com.zutubi.pulse.master.build.queue;

import com.zutubi.util.Predicate;

/**
 * A predicate that matches a request holder containing a
 * build request event with a specified owner
 *
 * @param <T> the specific subclass of RequestHolder that is being searched.
 */
public class RequestsByOwnerPredicate<T extends RequestHolder> implements Predicate<T>
{
    private Object owner;

    public RequestsByOwnerPredicate(Object owner)
    {
        this.owner = owner;
    }

    public boolean satisfied(RequestHolder holder)
    {
        return holder.getRequest().getOwner().equals(owner);
    }
}