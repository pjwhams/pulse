package com.zutubi.pulse.master.project.events;

import com.zutubi.pulse.master.tove.config.project.ProjectConfiguration;

/**
 * Initialisation has just completed for a project.  If it failed, there may be
 * an error message.
 */
public class ProjectInitialisationCompletedEvent extends ProjectLifecycleEvent
{
    private boolean successful;
    private String error;

    /**
     * Create an event to indicate that initialisation has completed for a
     * project.
     *
     * @param source               {@inheritDoc}
     * @param projectConfiguration {@inheritDoc}
     * @param successful           should be true if the initialisation
     *                             succeeded, false otherwise
     * @param error                an optional error message for failed
     *                             initialisation, may be null
     */
    public ProjectInitialisationCompletedEvent(Object source, ProjectConfiguration projectConfiguration, boolean successful, String error)
    {
        super(source, projectConfiguration);
        this.successful = successful;
        this.error = error;
    }

    /**
     * @return true iff the initialisation succeeded
     */
    public boolean isSuccessful()
    {
        return successful;
    }

    /**
     * @return an optional error message when {@link #isSuccessful()} is false
     *         (may be null)
     */
    public String getError()
    {
        return error;
    }

    public String toString()
    {
        String initResult;
        if (successful)
        {
            initResult = " Success";
        }
        else
        {
            initResult = " Failure";
            if (error != null)
            {
                initResult += ": " + error;
            }
        }

        return "Project Initialisation Completed: " + getProjectConfiguration().getName() + ": " + initResult;
    }
}
