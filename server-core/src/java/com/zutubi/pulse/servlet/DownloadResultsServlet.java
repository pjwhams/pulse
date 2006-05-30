/********************************************************************************
 @COPYRIGHT@
 ********************************************************************************/
package com.zutubi.pulse.servlet;

import com.zutubi.pulse.ServerRecipePaths;
import com.zutubi.pulse.bootstrap.CoreConfigurationManager;
import com.zutubi.pulse.bootstrap.ComponentContext;
import com.zutubi.pulse.util.FileSystemUtils;
import com.zutubi.pulse.util.IOUtils;
import com.zutubi.pulse.util.RandomUtils;
import com.zutubi.pulse.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 */
public class DownloadResultsServlet extends HttpServlet
{
    private static final Logger LOG = Logger.getLogger(DownloadResultsServlet.class);
    private CoreConfigurationManager configurationManager;

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    {
        String id = request.getParameter("recipe");

        try
        {
            long recipeId = Long.parseLong(id);
            boolean output = Boolean.parseBoolean(request.getParameter("output"));

            // lookup the recipe location, zip it up and write to output.
            ServerRecipePaths paths = new ServerRecipePaths(recipeId, getConfigurationManager().getUserPaths().getData());
            File dir;
            File zipFile;

            if (output)
            {
                dir = paths.getOutputDir();
                zipFile = new File(paths.getOutputZip().getAbsolutePath() + RandomUtils.randomString(10));
            }
            else
            {
                dir = paths.getBaseDir();
                zipFile = new File(paths.getBaseZip().getAbsolutePath() + RandomUtils.randomString(10));
            }

            try
            {
                FileSystemUtils.createZip(zipFile, dir, dir);

                response.setContentType("application/x-octet-stream");
                response.setContentLength((int) zipFile.length());

                FileInputStream input = null;

                try
                {
                    input = new FileInputStream(zipFile);
                    IOUtils.joinStreams(input, response.getOutputStream());
                }
                finally
                {
                    IOUtils.close(input);
                }

                response.getOutputStream().flush();
            }
            catch (FileNotFoundException e)
            {
                LOG.warning(e);
                response.sendError(404, "File not found: " + e.getMessage());
            }
            catch (IOException e)
            {
                LOG.warning(e);
                response.sendError(500, "I/O error: " + e.getMessage());
            }
        }
        catch (NumberFormatException e)
        {
            try
            {
                response.sendError(500, "Invalid recipe '" + id + "'");
            }
            catch (IOException e1)
            {
                e1.printStackTrace();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public CoreConfigurationManager getConfigurationManager()
    {
        if(configurationManager == null)
        {
            configurationManager = (CoreConfigurationManager) ComponentContext.getBean("configurationManager");
        }
        return configurationManager;
    }
}
