package com.cinnamonbob.api;

import com.cinnamonbob.BuildRequest;
import com.cinnamonbob.BobServer;

import java.util.List;
import java.util.Collections;

/**
 * A simple java object that defines the XmlRpc interface API.
 *
 */
public class XmlRpcApiHandler
{
    /**
     * Generate a request to enqueue a build request for the specified project.
     *
     * @param projectName
     * @return true if the request was enqueued, false otherwise.
     */
    public boolean build(String projectName)
    {
        // check if the projectName is valid.
        BuildRequest request = new BuildRequest(projectName);
        BobServer.build(projectName);
        return true;
    }

    /**
     * Retrieve a list of the projects in the system.
     *
     * @return
     */
    public List getProjects()
    {
        return Collections.EMPTY_LIST;
    }

}
