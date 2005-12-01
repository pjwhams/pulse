package com.cinnamonbob.model;

/**
 * A subversion revision, which is just a revision number.
 *
 * @author jsankey
 */
public class NumericalRevision extends Revision
{
    protected NumericalRevision()
    {
    }

    public NumericalRevision(long revisionNumber)
    {
        setRevisionString(Long.toString(revisionNumber));
    }

    public long getRevisionNumber()
    {
        try
        {
            return Long.parseLong(getRevisionString());
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }
}
