package com.zutubi.pulse.scm;

import com.zutubi.pulse.core.model.Changelist;
import com.zutubi.pulse.core.model.FileRevision;
import com.zutubi.pulse.core.model.Revision;
import com.zutubi.pulse.filesystem.remote.RemoteFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An interface for interaction with SCM servers.
 *
 * @author jsankey
 */
public interface SCMServer
{
    /**
     * Returns information about the server, as name-value pairs.
     *
     * @return a set of name-value pairs giving information about the server
     */
    Map<String, String> getServerInfo() throws SCMException;

    /**
     * Returns a string that uniquely identifies the server itself.  This may
     * include the server address and repository root, for example.  All
     * SCMServer objects talking to the same SCM should return the same id.
     *
     * @return a unique id for the SCM server
     */
    String getUid() throws SCMException;

    /**
     * Returns a summarised form of the location of the source this SCM has
     * been configured to check out.  For example, an subversion URL.
     *
     * @return a summarised form of the source location, fit for human consumption
     */
    String getLocation();

    /**
     * Run a check on the connection to the scm server.
     *
     * @throws SCMException if there are any problems.
     */
    void testConnection() throws SCMException;

    /**
     * Checks out a new working copy to the specified directory.
     *
     * @param id          an identifier for this checkout used to identify related
     *                    checkout/update operations.  May be null to indicate no
     *                    relationship.
     * @param toDirectory root directory to check the copy out to
     * @param revision    the revision to check out, or null for most recent (HEAD)
     * @param handler     if not null, receives notifications of events during the
     *                    checkout operation
     * @return the revision actually checked out
     * @throws SCMException if an error occurs communicating with the server
     */
    Revision checkout(String id, File toDirectory, Revision revision, SCMCheckoutEventHandler handler) throws SCMException;

    /**
     * Checkout the specified file.
     *
     * @param revision the revision be checked out
     * @param file     the path of the file relative to the configured scms checkout path
     * @return the contents of the requested file
     * @throws SCMException
     */
    String checkout(Revision revision, String file) throws SCMException;

    /**
     * Returns a list of changelists occuring in between the given revisions.
     * The changelist that created the from revision itself is NOT included in
     * the model.
     *
     * @param from  the revision before the first changelist to include in the model
     * @param to    the last revision to include in the model
     * @param paths an array of paths to restrict the query to, relative to the root
     *              of this connection to the server (a path of "" will include all
     *              changes)
     * @return a list of changelists that occured between the two revisions
     * @throws SCMException if an error occurs talking to the server
     */
    List<Changelist> getChanges(Revision from, Revision to, String ...paths) throws SCMException;

    /**
     * Returns a list of revisions occuring between the given revision and now.
     * The from revision itself it NOT included in the result.
     *
     * @param from the revision before the first revision to return
     * @return a list of revisions for all changes since from
     * @throws SCMException if an error occurs talking to the server
     */
    List<Revision> getRevisionsSince(Revision from) throws SCMException;

    /**
     * Returns a boolean indicated whether or not a change has occured since the specified revision.
     *
     * @param since
     * @return true if there has been a change
     * @throws SCMException
     */
    boolean hasChangedSince(Revision since) throws SCMException;

    /**
     * Returns the latest repository revision or null if it can not be determined.
     *
     * @return
     * @throws SCMException
     */
    Revision getLatestRevision() throws SCMException;

    /**
     * Returns details of a file or directory in the repository.
     *
     * @param path path to the file, relative to this connection's root
     * @return the file details
     */
    RemoteFile getFile(String path) throws SCMException;

    /**
     * Returns a list of all files/directories in the given path (which
     * should specify a directory).  This function is NOT recursive.
     *
     * @param path the path to list (relative to the root of the connection,
     *             i.e. an empty string is valid and means "list the root").
     * @return a list of files and directories contained within the given
     *         path
     */
    List<RemoteFile> getListing(String path) throws SCMException;

    /**
     * Update the working directory to the specified revision.
     *
     * @param id      an identifier for this update used to identify related
     *                checkout/update operations.  May be null to indicate no
     *                relationship.
     * @param workDir contains a local copy (checkout) of the module.
     * @param rev     revision to which the local copy will be updated.
     * @param handler if not null, receives notifications of events during the
     *                update operation
     * @throws SCMException
     */
    void update(String id, File workDir, Revision rev, SCMCheckoutEventHandler handler) throws SCMException;

    /**
     * Allows the scm server to indicate whether or not it supports the update
     * operation.
     *
     * @return true if update is supported, false otherwise.
     */
    boolean supportsUpdate();

    /**
     * Applies a tag to the given revision of all files in the server's view .
     *
     * @param revision     the revision to be tagged
     * @param name         the name of the tag, which has an SCM-specific format
     * @param moveExisting if true and a tag of the same name already exists,
     *                     that tag will be moved to the new revision and files
     * @throws SCMException
     */
    void tag(Revision revision, String name, boolean moveExisting) throws SCMException;

    Map<String, String> getConnectionProperties(String id, File dir) throws SCMException;
    void writeConnectionDetails(File outputDir) throws SCMException, IOException;

    /**
     * Returns the policy for line endings enforced at a client level, if any.
     *
     * @return the EOL policy, which will be EOLStyle.BINARY if no policy is
     *         in effect
     */
    FileStatus.EOLStyle getEOLPolicy() throws SCMException;

    /**
     * Gets the revision of a file at a given point in time, marked by a
     * repository revision.
     *
     * @param path         the path of the file to get the revision for
     * @param repoRevision the point in repository time to get the revision
     * @return the revision of the given path at that time, or null if the
     *         path did not exist at that time
     */
    FileRevision getFileRevision(String path, Revision repoRevision) throws SCMException;

    /**
     * Converts a string into a revision.  The string is input from the user,
     * and thus should be validated.  If it is invalid, an SCMException
     * should be thrown.
     *
     * @param revision revision input string to be converted into an actual
     *                 revision
     * @return a valid revision derived from the string
     * @throws SCMException if the given revision is invalid
     */
    Revision getRevision(String revision) throws SCMException;
}
