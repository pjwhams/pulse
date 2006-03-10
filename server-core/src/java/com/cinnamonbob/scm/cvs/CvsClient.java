package com.cinnamonbob.scm.cvs;

import com.cinnamonbob.core.model.Change;
import com.cinnamonbob.core.model.Changelist;
import com.cinnamonbob.core.model.CvsRevision;
import com.cinnamonbob.scm.SCMException;
import com.cinnamonbob.scm.cvs.client.ConnectionFactory;
import com.cinnamonbob.scm.cvs.client.CvsLogInformationListener;
import com.cinnamonbob.scm.cvs.client.LoggingListener;
import com.cinnamonbob.util.logging.Logger;
import com.opensymphony.util.TextUtils;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.command.checkout.CheckoutCommand;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.RlogCommand;
import org.netbeans.lib.cvsclient.command.tag.RtagCommand;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Allows for the system to interact with a cvs repository.
 * <p/>
 * This class is a wrapper around the org.netbeans.lib.cvsclient package, using the
 * netbeans package to handle the cvs protocol requirements.
 */
public class CvsClient
{
    /**
     * The date format used when sending dates to the CVS server.
     * ... is this correct???...
     */
    private static final SimpleDateFormat CVSDATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'GMT'");

    /**
     * Logging.
     */
    private static final Logger LOG = Logger.getLogger(CvsClient.class);

    /**
     * The $CVSROOT, it defines the details of which cvs repository is being worked with.
     */
    private final CVSRoot root;

    /**
     * The local path to the working repository directories. This is required for checkout / update
     * commands.
     */
    private File localPath;

    /**
     * The tag is an optional parameter. If specified, all cvs requests will be made with respect
     * to the specified tag. If not, the default tag, 'HEAD' is assumed.
     */
    private String tag;

    /**
     * @param cvsRoot
     * @throws IllegalArgumentException if the cvsRoot parameter is invalid.
     */
    public CvsClient(String cvsRoot) throws IllegalArgumentException
    {
        this(CVSRoot.parse(cvsRoot));
    }

    public CvsClient(CVSRoot root)
    {
        this.root = root;

        //TODO: Integrate the following logging into the systems logging. This information
        //      will be very useful in tracking problems with the cvs client integration.
        //      It will likely require patching the cvsclient.util.Logger code.
        //org.netbeans.lib.cvsclient.util.Logger.setLogging("system");
    }

    /**
     * Set the path to the local copy of the repository. This is required
     * for commands that work with a local copy of the repository, such as update
     * and checkout.
     * <p/>
     * If this path does not exist, then the client will create it.
     */
    public void setLocalPath(File path)
    {
        this.localPath = path;
    }

    /**
     * Set the working tag. This string can represent either a branch or a tag.
     *
     * @param tag
     */
    public void setTag(String tag)
    {
        this.tag = tag;
    }

    /**
     * Checkout the specified module.
     *
     * @param module
     *
     * @throws SCMException
     */
    public void checkout(String module) throws SCMException
    {
        checkout(module, null);
    }

    /**
     * Checkout the specified module, as it was on the specified date. If the date is null,
     * no date restriction will be applied.
     *
     */
    public void checkout(String module, Date date) throws SCMException
    {
        module = checkModule(module);

        Connection connection = null;
        try
        {
            GlobalOptions globalOptions = new GlobalOptions();
            globalOptions.setCVSRoot(root.toString());

            connection = ConnectionFactory.getConnection(root);
            connection.open();

            Client client = new Client(connection, new StandardAdminHandler());
            client.getEventManager().addCVSListener(new LoggingListener());

            CheckoutCommand checkout = new CheckoutCommand();
            checkout.setModule(module);
            checkout.setPruneDirectories(true);
            if (localPath == null)
            {
                throw new IllegalArgumentException("Please specify a local path before attempting to checkout.");
            }
            client.setLocalPath(localPath.getAbsolutePath());

            // bind the checkout to the specified tag.
            if (tag != null)
            {
                checkout.setCheckoutByRevision(tag);
            }

            // bind the checkout to the specified date.
            if (date != null)
            {
                checkout.setCheckoutByDate(CVSDATE.format(date));
            }

            if (!client.executeCommand(checkout, globalOptions))
            {
                throw new SCMException("checkout failed..");
            }
        }
        catch (AuthenticationException ae)
        {
            throw new SCMException(ae);
        }
        catch (CommandAbortedException cae)
        {
            throw new SCMException(cae);
        }
        catch (CommandException ce)
        {
            throw new SCMException(ce);
        }
        finally
        {
            // cleanup any resources used by this command.
            CvsUtils.close(connection);
        }
    }

    /**
     * Check the value of the module string.
     *
     * @param module
     */
    private String checkModule(String module)
    {
        if (!TextUtils.stringSet(module))
        {
            throw new IllegalArgumentException("Command requires a module.");
        }

        // HACK: cvs client has trouble absolute references, hanging if they are invalid.
        // Therefore, do not allow them.
        while (module.startsWith("/"))
        {
            module = module.substring(1);
        }
        return module;
    }

    /**
     * Check if the repository has been updated since the since specified. Note, the
     * updates are restricted to those that imply a change to the source. That is, commit,
     * add and remove operations.
     *
     * @param since
     * @return true if the cvs repository has been updated.
     */
    public boolean hasChangedSince(Date since, String module) throws SCMException
    {
        return getLastUpdate(since, module) != null;
    }

    /**
     * @param since
     * @return null indicates no change since the specified date
     * @throws SCMException
     */
    public Date getLastUpdate(Date since, String module) throws SCMException
    {
        List<LogInformation.Revision> changes = rlog(since, module);
        if (changes.size() == 0)
        {
            return null;
        }
        // need to ensure that the log information is ordered by date...
        LogInformation.Revision latestChange = changes.get(changes.size() - 1);
        return latestChange.getDate();
    }

    /**
     * Update is not yet supported.
     */
    public void update()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Tag the remote repository.
     *
     * @param tag
     * @param module
     * @param date
     *
     * @throws SCMException
     */
    public void tag(String tag, String module, Date date) throws SCMException
    {
        // WARNING: This has not been tested...
        Connection connection = null;
        try
        {
            GlobalOptions globalOptions = new GlobalOptions();
            globalOptions.setCVSRoot(root.toString());

            connection = ConnectionFactory.getConnection(root);
            connection.open();

            Client client = new Client(connection, new StandardAdminHandler());

            RtagCommand rtag = new RtagCommand();
            rtag.setTag(tag);
            rtag.setOverrideExistingTag(true);
            rtag.setModules(new String[]{module});
            if (date != null)
            {
                rtag.setTagByDate(CVSDATE.format(date));
            }
            client.executeCommand(rtag, globalOptions);
        }
        catch (AuthenticationException ae)
        {
            throw new SCMException(ae);
        }
        catch (CommandAbortedException cae)
        {
            throw new SCMException(cae);
        }
        catch (CommandException ce)
        {
            throw new SCMException(ce);
        }
        finally
        {
            CvsUtils.close(connection);
        }
    }

    //-------------------------------------------------------------------------
    // change set analysis:
    // - cvs changes are not atomic. therefore,
    //    - a change set does not need to occur at the same time
    //    - multiple changesets can be interlevered.
    // characteristics of changesets:
    // - a) single author.
    // - b) single commit statement.
    // - c) each file appears only once.
    // - d) changeset bound to a single branch.
    // - e) contiguous block of time.

    // group by (author,branch,comment)

    /**
     * Retrieve all of the change lists in the named module in the repository.
     *
     * @return
     * @throws SCMException
     */
    public List<Changelist> getChangeLists(String module) throws SCMException
    {
        return getChangeLists(null, module);
    }

    /**
     * Retrieve the list of changes in the named module since the specified date.
     * @param since
     * @param module
     * @return
     * @throws SCMException
     */
    public List<Changelist> getChangeLists(Date since, String module) throws SCMException
    {
        // retrieve the log info for all of the files that have been modified.
        List<LogInformation.Revision> logInfos = rlog(since, module);

        // extract the individual changes associated with the history data and the associated
        // information.
        List<LocalChange> simpleChanges = new LinkedList<LocalChange>();

        for (LogInformation.Revision logInfo : logInfos)
        {
            LocalChange o = new LocalChange(logInfo);
            // is this appropriate? how do we distinguish a branch from a tag?
            // do we need to?
            o.setTag(tag);
            simpleChanges.add(o);
        }

        // group by author, branch, sort by date. this will have the affect of grouping
        // all of the changes in a single changeset together, ordered by date.
        Collections.sort(simpleChanges, new Comparator<LocalChange>()
        {
            public int compare(LocalChange changeA, LocalChange changeB)
            {
                // null author?? - revision miss-match..
                int comparison = changeA.getAuthor().compareTo(changeB.getAuthor());
                if (comparison != 0)
                {
                    return comparison;
                }
                comparison = changeA.getTag().compareTo(changeB.getTag());
                if (comparison != 0)
                {
                    return comparison;
                }
                return changeA.getDate().compareTo(changeB.getDate());
            }
        });

        // create change sets by author. ie: each change set object will contain
        // all of the changes made by a particular author.
        List<LocalChangeSet> changeSets = new LinkedList<LocalChangeSet>();
        LocalChangeSet changeSet = null;
        for (LocalChange change : simpleChanges)
        {
            if (changeSet == null)
            {
                changeSet = new LocalChangeSet(change);
            }
            else
            {
                if (changeSet.belongsTo(change))
                {
                    changeSet.add(change);
                }
                else
                {
                    changeSets.add(changeSet);
                    changeSet = new LocalChangeSet(change);
                }
            }
        }
        if (changeSet != null)
        {
            changeSets.add(changeSet);
        }

        // refine the changesets, splitting it up according to file names. ie: duplicate filenames
        // should trigger a new changeset.
        List<LocalChangeSet> refinedSets = new LinkedList<LocalChangeSet>();
        for (LocalChangeSet set : changeSets)
        {
            refinedSets.addAll(set.refine());
        }

        // now that we have the changeset information, lets create the final product.
        List<Changelist> changelists = new LinkedList<Changelist>();
        for (LocalChangeSet set : refinedSets)
        {
            List<LocalChange> localChanges = set.getChanges();
            // we use the last change because it has the most recent date. all the other information is
            // is common to all the changes.
            LocalChange lastChange = localChanges.get(localChanges.size() - 1);
            CvsRevision revision = new CvsRevision(lastChange.getAuthor(), lastChange.getTag(), lastChange.getMessage(), lastChange.getDate());
            Changelist changelist = new Changelist(revision);
            for (LocalChange change : localChanges)
            {
                changelist.addChange(new Change(change.getFilename(), change.getRevision(), change.getAction()));
            }
            changelists.add(changelist);
        }

        return changelists;
    }

    /**
     * This rlog command returns a list of LogInformation.Revision instances that define the
     * individual files and there revisions that were generated since the specified date in the
     * named module. These revisions are ordered chronologically.
     *
     * @param since
     * @param module
     * @return
     * @throws SCMException
     */
    public List<LogInformation.Revision> rlog(Date since, String module) throws SCMException
    {
        Connection connection = null;
        try
        {
            GlobalOptions globalOptions = new GlobalOptions();
            globalOptions.setCVSRoot(root.toString());

            connection = ConnectionFactory.getConnection(root);
            connection.open();

            final List<LogInformation> rlogResponse = new LinkedList<LogInformation>();

            Client client = new Client(connection, new StandardAdminHandler());

            // the local path is not important for the RLogCommand, but needs to exist... go figure..
            // should we try setting the real local path if it exists?
            client.setLocalPath("/some/local/path");

            client.getEventManager().addCVSListener(new CvsLogInformationListener(rlogResponse));

            RlogCommand log = new RlogCommand();
            log.setModule(module);

            // if a date is specified, then filter the request by that date.
            if (since != null)
            {
                // since is the lower bound, now is the upper bound.
                Date now = new Date();
                log.setDateFilter(CVSDATE.format(since) + "<" + CVSDATE.format(now));
            }

            // bind to the specified tag, or else use head.
            if (tag != null)
            {
                log.setRevisionFilter(tag);
            }
            else
            {
                log.setDefaultBranch(true);
            }

            client.executeCommand(log, globalOptions);

            // extract the returned revisions, and order them chronologically.
            List<LogInformation.Revision> revisions = new LinkedList<LogInformation.Revision>();
            for (LogInformation logInfo : rlogResponse)
            {
                for (Object obj : logInfo.getRevisionList())
                {
                    LogInformation.Revision rev = (LogInformation.Revision) obj;
                    revisions.add(rev);
                }
            }

            // sort these revisions by date.
            Collections.sort(revisions, new Comparator<LogInformation.Revision>()
            {
                public int compare(LogInformation.Revision o1, LogInformation.Revision o2)
                {
                    return o1.getDate().compareTo(o2.getDate());
                }
            });

            return revisions;
        }
        catch (AuthenticationException ae)
        {
            throw new SCMException(ae);
        }
        catch (CommandException ce)
        {
            throw new SCMException(ce);
        }
        finally
        {
            // cleanup any resources used by this command.
            CvsUtils.close(connection);
        }
    }

    /**
     * Simple value object used to help store data during the changeset analysis process.
     */
    private class LocalChange
    {
        private LogInformation.Revision log;

        private String tag;

        public LocalChange(LogInformation.Revision log)
        {
            if (log == null)
            {
                throw new IllegalArgumentException("Log Information cannot be null.");
            }
            this.log = log;
        }

        public String getAuthor()
        {
            return log.getAuthor();
        }

        public String getRevision()
        {
            return log.getNumber();
        }

        public String getTag()
        {
            if (tag == null)
            {
                return "";
            }
            return tag;
        }

        public void setTag(String branch)
        {
            this.tag = branch;
        }

        public Date getDate()
        {
            return log.getDate();
        }

        public String getMessage()
        {
            return log.getMessage();
        }

        public String getFilename()
        {
            // need to process the filename.

            String filename = log.getLogInfoHeader().getRepositoryFilename();

            // remove the ,v
            if (filename.endsWith(",v"))
                filename = filename.substring(0, filename.length() -2);

            // remove the repo root.
            if (filename.startsWith(root.getRepository()))
                filename = filename.substring(root.getRepository().length());

            return filename;
        }

        public Change.Action getAction()
        {
            if (log.getAddedLines() == 0 && log.getRemovedLines() == 0)
            {
                if (!log.getState().equalsIgnoreCase("dead"))
                {
                    return Change.Action.ADD;
                }
                return Change.Action.DELETE;
            }
            return Change.Action.EDIT;
        }

    }


    /**
     * Simple value object used to help store data during the changeset analysis process.
     */
    private class LocalChangeSet
    {
        private final List<LocalChange> changes = new LinkedList<LocalChange>();

        LocalChangeSet(LocalChange c)
        {
            changes.add(c);
        }

        void add(LocalChange c)
        {
            changes.add(c);
        }

        boolean belongsTo(LocalChange otherChange)
        {
            if (changes.size() == 0)
            {
                return true;
            }

            LocalChange previousChange = changes.get(0);
            return previousChange.getAuthor().equals(otherChange.getAuthor()) &&
                    previousChange.getTag().equals(otherChange.getTag()) &&
                    previousChange.getMessage().equals(otherChange.getMessage());
        }

        /**
         *
         */
        public List<LocalChangeSet> refine()
        {
            Map<String, String> filenames = new HashMap<String, String>();
            List<LocalChangeSet> changesets = new LinkedList<LocalChangeSet>();

            LocalChangeSet changeSet = null;
            for (LocalChange change : changes)
            {
                if (filenames.containsKey(change.getFilename()))
                {
                    // time for a new changeset.
                    filenames.clear();
                    changesets.add(changeSet);
                    filenames.put(change.getFilename(), change.getFilename());
                    changeSet = new LocalChangeSet(change);
                }
                else
                {
                    filenames.put(change.getFilename(), change.getFilename());
                    if (changeSet == null)
                    {
                        changeSet = new LocalChangeSet(change);
                    }
                    else
                    {
                        changeSet.add(change);
                    }
                }
            }
            if (changeSet != null)
            {
                changesets.add(changeSet);
            }
            return changesets;
        }

        public List<LocalChange> getChanges()
        {
            return changes;
        }

    }

}
