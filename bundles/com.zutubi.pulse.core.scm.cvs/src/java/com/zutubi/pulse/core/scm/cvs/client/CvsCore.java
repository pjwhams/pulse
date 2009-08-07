package com.zutubi.pulse.core.scm.cvs.client;

import com.zutubi.pulse.core.scm.api.ScmException;
import com.zutubi.pulse.core.scm.api.ScmFeedbackHandler;
import com.zutubi.pulse.core.scm.cvs.CvsRevision;
import com.zutubi.pulse.core.scm.cvs.CvsServerCapabilities;
import com.zutubi.pulse.core.scm.cvs.client.commands.*;
import com.zutubi.pulse.core.scm.cvs.client.util.CvsUtils;
import com.zutubi.util.Constants;
import com.zutubi.util.FileSystemUtils;
import com.zutubi.util.StringUtils;
import com.zutubi.util.io.IOUtils;
import com.zutubi.util.logging.Logger;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.Command;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.diff.DiffCommand;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.RlogCommand;
import org.netbeans.lib.cvsclient.command.status.StatusCommand;
import org.netbeans.lib.cvsclient.command.status.StatusInformation;
import org.netbeans.lib.cvsclient.command.tag.RtagCommand;
import org.netbeans.lib.cvsclient.command.update.UpdateCommand;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.CVSListener;
import org.netbeans.lib.cvsclient.event.MessageEvent;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class CvsCore
{
    public static final Logger LOG = Logger.getLogger(CvsCore.class);

    private static int COMMAND_COUNT = 0;

    private final SimpleDateFormat dateFormat;

    // set up some skullduggery to allow us to capture and control the logging generated by the javacvs package
    static
    {
        try
        {
            Field outLogStream = org.netbeans.lib.cvsclient.util.Logger.class.getDeclaredField("outLogStream");
            Field inLogStream = org.netbeans.lib.cvsclient.util.Logger.class.getDeclaredField("inLogStream");
            Field logging = org.netbeans.lib.cvsclient.util.Logger.class.getDeclaredField("logging");

            outLogStream.setAccessible(true);
            inLogStream.setAccessible(true);
            logging.setAccessible(true);

            outLogStream.set(org.netbeans.lib.cvsclient.util.Logger.class, new LoggingOutputStream(LOG, Level.FINER));
            inLogStream.set(org.netbeans.lib.cvsclient.util.Logger.class, new LoggingOutputStream(LOG, Level.FINEST));
            logging.set(org.netbeans.lib.cvsclient.util.Logger.class, Boolean.TRUE);
        }
        catch (Exception e)
        {
            LOG.warning(e);
        }
    }

    private CVSRoot root;
    private String password;

    public CvsCore()
    {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public void setRoot(CVSRoot root)
    {
        this.root = root;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String version() throws ScmException
    {
        VersionCommand version = new VersionCommand();

        if (!executeCommand(version, null, null))
        {
            throw new ScmException("Failed to retrieve the cvs server version details.");
        }

        // extract the actual version string to assist with processing.
        // ... x.xx?.xx?yyy ... (x digit, y alphanumeric, ... non digit)
        Pattern p = Pattern.compile("\\D*([0-9].[0-9][0-9]?.[0-9][0-9]?[\\w]*)\\D*");
        Matcher matcher = p.matcher(version.getVersion());
        if (matcher.matches())
        {
            return matcher.group(1);
        }

        return version.getVersion();
    }

    public void update(File workingDirectory, CvsRevision revision, ScmFeedbackHandler handler) throws ScmException
    {
        UpdateListener listener = null;
        if (handler != null)
        {
            try
            {
                // strip the relative repo path from the working directory to determine the relative repository root.
                String path = FileSystemUtils.normaliseSeparators(workingDirectory.getCanonicalPath());
                String repoPath = readRespositoryPath(workingDirectory);
                if (StringUtils.stringSet(repoPath))
                {
                    if (path.endsWith(repoPath))
                    {
                        path = path.substring(0, path.lastIndexOf(repoPath));
                    }
                    else
                    {
                        LOG.warning("Expected path '" + path + "' to end with '" + repoPath + "'");
                    }
                }

                listener = new UpdateListener(handler, new File(path));
            }
            catch (IOException e)
            {
                LOG.warning(e);
            }
        }
        update(workingDirectory, revision, listener);
    }

    private String readRespositoryPath(File dir)
    {
        try
        {
            File repositoryFile = new File(dir, FileSystemUtils.join("CVS", "Repository"));
            if (!repositoryFile.isFile())
            {
                // ... the named directory is not part of a CVS repository checkout? ...
                LOG.error(repositoryFile.getCanonicalPath() + " does not exist.");
                return null;
            }
            return IOUtils.fileToString(repositoryFile);
        }
        catch (IOException e)
        {
            LOG.error(e);
            return null;
        }
    }

    public void update(File workingDirectory, CvsRevision revision, CVSListener listener) throws ScmException
    {
        UpdateCommand update = new UpdateCommand();
        update.setPruneDirectories(true);
        update.setBuildDirectories(true);
        update.setResetStickyOnes(true);

        if (revision != null)
        {
            if (StringUtils.stringSet(revision.getBranch()) && revision.getDate() != null)
            {
                // -r TAG[:date] (format only supported by some cvs servers).
                String rev = revision.getBranch() + ":" + dateFormat.format(revision.getDate());
                update.setUpdateByRevision(rev);
            }
            else if (StringUtils.stringSet(revision.getBranch()))
            {
                update.setUpdateByRevision(revision.getBranch());
            }
            else if (revision.getDate() != null)
            {
                update.setUpdateByDate(dateFormat.format(revision.getDate()));
            }
        }

        if (!executeCommand(update, workingDirectory, listener))
        {
            throw new ScmException("Failed to update.");
        }
    }

    public void checkout(File workdir, String module, CvsRevision revision, ScmFeedbackHandler handler) throws ScmException
    {
        checkout(workdir, module, revision, true, handler);
    }

    public void checkout(File workdir, String module, CvsRevision revision, boolean recursive, ScmFeedbackHandler handler) throws ScmException
    {
        CheckoutCommand checkout = new CheckoutCommand();
        checkout.setModule(module);
        checkout.setRecursive(recursive);

        if (revision != null)
        {
            if (StringUtils.stringSet(revision.getBranch()))
            {
                checkout.setCheckoutByRevision(revision.getBranch());
            }
            if (revision.getDate() != null)
            {
                checkout.setCheckoutByDate(dateFormat.format(revision.getDate()));
            }
        }

        CheckoutListener listener = null;
        if (handler != null)
        {
            listener = new CheckoutListener(handler, workdir);
        }

        if (!executeCommand(checkout, workdir, listener))
        {
            throw new ScmException("Failed to checkout.");
        }
    }

    public void tag(String module, CvsRevision revision, String name) throws ScmException
    {
        tag(module, revision, name, false);
    }

    public void tag(String module, CvsRevision revision, String name, boolean moveExisting) throws ScmException
    {
        RtagCommand tag = new RtagCommand();
        tag.setModules(new String[]{module});
        tag.setTag(name);
        tag.setOverrideExistingTag(moveExisting);
        tag.setRecursive(true);

        if (revision != null)
        {
            if (StringUtils.stringSet(revision.getBranch()))
            {
                tag.setTagByRevision(revision.getBranch());
            }
            if (revision.getDate() != null)
            {
                tag.setTagByDate(dateFormat.format(revision.getDate()));
            }
        }
        else
        {
            tag.setMatchHeadIfRevisionNotFound(true);
        }

        if (!executeCommand(tag, null, null))
        {
            throw new ScmException("Failed to tag.");
        }
    }

    public void deleteTag(String module, String name) throws ScmException
    {
        RtagCommand tag = new RtagCommand();
        tag.setModules(new String[]{module});
        tag.setTag(name);
        tag.setDeleteTag(true);
        tag.setRecursive(true);
        tag.setClearFromRemoved(true);

        if (!executeCommand(tag, null, null))
        {
            throw new ScmException("Failed to delete tag.");
        }
    }

    public List<LogInformation> rlog(String module, CvsRevision from, CvsRevision to) throws ScmException
    {
        RlogCommand rlog = new RlogCommand();
        rlog.setModule(module);

        // allow users to bypass 
        boolean useSuppressHeader = CvsServerCapabilities.supportsRlogSuppressHeader();
        rlog.setSuppressHeader(useSuppressHeader);

        String branch = from == null ? to == null ? null : to.getBranch() : from.getBranch();
        if (StringUtils.stringSet(branch))
        {
            rlog.setRevisionFilter(branch);
        }
        else
        {
            rlog.setDefaultBranch(true);
        }

        String dateFilter = "";
        String del = "<=";
        if (from != null && from.getDate() != null)
        {
            dateFilter = dateFormat.format(from.getDate()) + del;
            del = "";
        }
        if (to != null && to.getDate() != null)
        {
            dateFilter = dateFilter + del + dateFormat.format(to.getDate());
        }
        if (StringUtils.stringSet(dateFilter))
        {
            rlog.setDateFilter(dateFilter);
        }

        LogListener listener = new LogListener();
        if (!executeCommand(rlog, null, listener))
        {
            throw new ScmException("Failed to retrieve the cvs server changes between revisions.");
        }
        return listener.getLogInfo();
    }

    public List<StatusInformation> status(File workingCopy) throws ScmException
    {
        StatusListener listener = new StatusListener();
        status(workingCopy, null, listener);
        return listener.getInfo();
    }

    public void status(File workingCopy, File[] files, CVSListener listener) throws ScmException
    {
        StatusCommand status = new StatusCommand();
        status.setRecursive(true);
        if (files == null)
        {
            status.setFiles(new File[]{workingCopy});
        }
        else
        {
            status.setFiles(files);
        }

        if (!executeCommand(status, workingCopy, listener))
        {
            throw new ScmException("Failed to run status command.  Please check the log for details.");
        }
    }

    public List<RlsInfo> list(String path) throws ScmException
    {
        RlsCommand rls = new RlsCommand();
        rls.setDisplayInEntriesFormat(true); // ensure that we get the appropriate response from the server
        rls.setPaths(path);

        if (!executeCommand(rls, null, null))
        {
            throw new ScmException("Failed to run the rls command.  Please check logs for details.");
        }

        return rls.getListing();
    }

    /**
     * Check that a connection can be opened to the cvs server.
     *
     * @throws ScmException is thrown if the check fails.
     */
    public void testConnection() throws ScmException
    {
        Connection connection = null;
        try
        {
            connection = ConnectionFactory.getConnection(root, password);
            connection.verify();
        }
        catch (AuthenticationException e)
        {
            throw new ScmException(e);
        }
        finally
        {
            CvsUtils.close(connection);
        }
    }

    public void diff(File base, String path, OutputStream output) throws ScmException
    {
        DiffCommand diffCommand = new DiffCommand();
        diffCommand.setUnifiedDiff(true);
        diffCommand.setFiles(new File[]{ new File(base, path) });
        executeCommand(diffCommand, base, new OutputListener(new PrintWriter(output)));
    }

    /**
     * Execute the cvs command.
     *
     * @param command   to be executed on the configured cvs connection.
     * @param localPath local path which the command refers to (may be null)
     * @param listener  receives feedback from executing the command (may be
     *                  null)
     * @return true if the command is successful, false otherwise.
     * @throws ScmException thrown when an error occurs.
     */
    public boolean executeCommand(Command command, File localPath, CVSListener listener) throws ScmException
    {
        Connection connection = null;
        try
        {
            GlobalOptions globalOptions = new GlobalOptions();
            globalOptions.setCVSRoot(root.toString());

            connection = openConnection();

            Client client = new Client(connection, new StandardAdminHandler());
            if (listener != null)
            {
                client.getEventManager().addCVSListener(listener);
            }
            if (localPath != null)
            {
                client.setLocalPath(localPath.getAbsolutePath());
            }

            client.getEventManager().addCVSListener(new CVSAdapter()
            {
                public void messageSent(MessageEvent e)
                {
                    LOG.finer(e.getMessage() + "\n");
                }
            });

            long time = System.currentTimeMillis();
            try
            {
                LOG.info("Executing command: 'cvs -d " + root + " " + command.getCVSCommand() + "'.");
                if (!client.executeCommand(command, globalOptions))
                {
                    LOG.warning("Command 'cvs -d " + root + " " + command.getCVSCommand() + "' has failed.");
                    return false;
                }
                return true;
            }
            finally
            {
                LOG.finer("Elapsed time: " + ((System.currentTimeMillis() - time) / Constants.SECOND) + " second(s)");
            }
        }
        catch (AuthenticationException ae)
        {
            throw new ScmException(ae);
        }
        catch (CommandAbortedException cae)
        {
            throw new ScmException(cae);
        }
        catch (CommandException ce)
        {
            throw new ScmException(ce);
        }
        finally
        {
            CvsUtils.close(connection);
        }
    }

    /**
     * Open a new connection to the cvs server.
     *
     * @return the newly created connection.
     *
     * @throws AuthenticationException if authentication fails.
     * @throws CommandAbortedException is thrown on error
     */
    private Connection openConnection() throws AuthenticationException, CommandAbortedException
    {
        Connection connection = ConnectionFactory.getConnection(root, password);
        connection.open();
        return connection;
    }
}
