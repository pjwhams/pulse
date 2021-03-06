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

package com.zutubi.pulse.core.scm.p4;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.pulse.core.engine.api.ResourceProperty;
import com.zutubi.pulse.core.scm.CachingScmClient;
import com.zutubi.pulse.core.scm.CachingScmFile;
import com.zutubi.pulse.core.scm.ScmFileCache;
import com.zutubi.pulse.core.scm.api.*;
import com.zutubi.pulse.core.scm.config.api.ScmConfiguration;
import com.zutubi.pulse.core.scm.p4.config.PerforceConfiguration;
import com.zutubi.pulse.core.scm.patch.api.FileStatus;
import com.zutubi.pulse.core.scm.patch.api.PatchInterceptor;
import com.zutubi.util.Constants;
import com.zutubi.util.SecurityUtils;
import com.zutubi.util.StringUtils;
import com.zutubi.util.io.FileSystemUtils;
import com.zutubi.util.logging.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Collections2.transform;
import static com.zutubi.pulse.core.scm.p4.PerforceConstants.*;

public class PerforceClient extends CachingScmClient implements PatchInterceptor
{
    private static final Logger LOG = Logger.getLogger(PerforceClient.class);

    public static final String TYPE = "p4";

    // Output of p4 sync -f:
    //   <depot file>#<revision> - (refreshing|added as) <local file>
    //   <depot file>#<revision> - (refreshing|updating|added as|deleted as) <local file>
    //   ...
    private static final Pattern SYNC_PATTERN = Pattern.compile("^(.+)#([0-9]+) - (refreshing|updating|added as|deleted as) (.+)$", Pattern.MULTILINE);
    /**
     * Used to limit the number of files we'll pass as arguments to commands in
     * one go, lest we hit some command or OS limit.
     */
    private static final int FILE_LIMIT = 32;
    /**
     * Older versions of Perforce don't support the -m flag to limit the number
     * of files reported by p4 describe.  The exact version it was introduced
     * is unclear due to bad docs, so instead of parsing versions we just
     * assume it works until we get an error stating otherwise.  In that case
     * this flag is set to false and we don't try using it again.
     */
    private static final AtomicBoolean DESCRIBE_SUPPORTS_LIMIT = new AtomicBoolean(true);

    private PerforceConfiguration configuration;
    private PerforceCore core;
    private PerforceWorkspaceManager workspaceManager;

    public Revision getLatestRevision(ScmContext context) throws ScmException
    {
        core.setContext(context.getEnvironmentContext());
        PerforceWorkspace workspace = workspaceManager.allocateWorkspace(core, configuration, context);
        try
        {
            return getLatestRevision(workspace);
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    private Revision getLatestRevision(PerforceWorkspace workspace) throws ScmException
    {
        return core.getLatestRevisionForFiles(workspace.getName(), "//" + workspace.getName() + "/...");
    }

    public void populate(ScmContext context, ScmFileCache.CacheItem item) throws ScmException
    {
        core.setContext(context.getEnvironmentContext());
        item.cachedRevision = getLatestRevision(context);
        item.cachedListing = new TreeMap<String, CachingScmFile>();

        CachingScmFile rootFile = new CachingScmFile("", true);
        item.cachedListing.put("", rootFile);

        PerforceWorkspace workspace = workspaceManager.allocateWorkspace(core, configuration, context);
        try
        {
            PerforceCore.P4Result result = core.runP4(null, getP4Command(COMMAND_SYNC), FLAG_CLIENT, workspace.getName(), COMMAND_SYNC, FLAG_FORCE, FLAG_PREVIEW);
            Matcher matcher = SYNC_PATTERN.matcher(result.stdout);
            while (matcher.find())
            {
                String localFile = matcher.group(4);
                if (localFile.startsWith(workspace.getRoot()))
                {
                    localFile = localFile.substring(workspace.getRoot().length());
                }

                // Separators must be normalised
                localFile = FileSystemUtils.normaliseSeparators(localFile);
                if (localFile.startsWith("/"))
                {
                    localFile = localFile.substring(1);
                }

                addToCache(localFile, rootFile, item);
            }
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    private Changelist getChangelist(String clientName, long number, boolean includeFiles) throws ScmException
    {
        //   Change <number> by <user>@<client> on <date> <time> (*pending*)?
        //
        //           <message, wrapped and indented>
        //
        //   Affected files ...
        //
        //   ... <file>#<revision> <action>
        //   ... <file>#<revision> <action>
        //   ...
        PerforceCore.P4Result result;
        // Even when we aren't including files in the result, if we are applying filters we need to list the files so
        // we can determine if the entire change should be filtered out.
        boolean fileListingRequired = includeFiles || configuration.filtersPaths();
        // When we don't need to list the files, we set max files to 1 (0 doesn't work, it appears to mean "no limit").
        int fileLimit = -1;
        if (DESCRIBE_SUPPORTS_LIMIT.get())
        {
            fileLimit = fileListingRequired ? Integer.getInteger("pulse.p4.changelist.file.limit", -1) : 1;
        }

        if (fileLimit <= 0)
        {
            result = core.runP4(false, null, getP4Command(COMMAND_DESCRIBE), FLAG_CLIENT, clientName, COMMAND_DESCRIBE, FLAG_SHORT, Long.toString(number));
        }
        else
        {
            try
            {
                result = core.runP4(false, null, getP4Command(COMMAND_DESCRIBE), FLAG_CLIENT, clientName, COMMAND_DESCRIBE, FLAG_SHORT, FLAG_MAXIMUM, Integer.toString(fileLimit), Long.toString(number));
            }
            catch (ScmException e)
            {
                if (e.getMessage().contains("Invalid option: -m"))
                {
                    DESCRIBE_SUPPORTS_LIMIT.set(false);
                    return getChangelist(clientName, number, includeFiles);
                }
                else
                {
                    throw new ScmException(e.getMessage(), e);
                }
            }
        }

        if (result.stderr.length() > 0)
        {
            if (result.stderr.indexOf("no such changelist") >= 0)
            {
                // OK, this change must have been deleted at some point
                // (CIB-1010).
                return null;
            }
            else
            {
                throw new ScmException("p4 process returned error '" + result.stderr.toString().trim() + "'");
            }
        }

        String[] lines = core.splitLines(result);

        if (lines.length < 1)
        {
            throw new ScmException("Unexpected output from 'p4 describe -s " + Long.toString(number) + "'");
        }

        Pattern re = Pattern.compile("Change ([0-9]+) by (.+)@(.+) on ([0-9/]+ [0-9:]+)( \\*pending\\*)?");
        Matcher matcher = re.matcher(lines[0].trim());
        String user;
        Date date;

        if (matcher.matches())
        {
            if (matcher.group(5) != null)
            {
                // Change is marked *pending*
                return null;
            }

            user = matcher.group(2);

            try
            {
                SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                date = format.parse(matcher.group(4));
            }
            catch (ParseException e)
            {
                throw new ScmException("Unable to parse date from p4 describe", e);
            }
        }
        else
        {
            throw new ScmException("Unexpected first line of output from p4 describe '" + lines[0] + "'");
        }

        int affectedFilesIndex;

        for (affectedFilesIndex = lines.length - 1; affectedFilesIndex > 0; affectedFilesIndex--)
        {
            if (lines[affectedFilesIndex].startsWith("Affected files ..."))
            {
                break;
            }
        }

        String comment = getChangelistComment(lines, affectedFilesIndex);

        Revision revision = new Revision(Long.toString(number));

        int firstFileIndex = affectedFilesIndex + 2;
        int fileCount = lines.length - firstFileIndex;
        List<FileChange> changes = new ArrayList<FileChange>(fileCount);
        if (fileListingRequired)
        {
            if (fileCount == fileLimit)
            {
                // If we hit the file limit we simply don't apply filters to this change, as it may include files that
                // pass the filter but we didn't see due to limiting.
                LOG.debug("Not applying filters to changelist '" + revision + "': file limit reached");
                for (int i = firstFileIndex; i < lines.length; i++)
                {
                    changes.add(getChangelistChange(lines[i]));
                }
            }
            else
            {
                FilterPathsPredicate predicate = new FilterPathsPredicate(configuration.getIncludedPaths(), configuration.getExcludedPaths());
                boolean fileExcluded = false;
                for (int i = firstFileIndex; i < lines.length; i++)
                {
                    FileChange change = getChangelistChange(lines[i]);
                    if (predicate.apply(change.getPath()))
                    {
                        changes.add(change);
                    }
                    else
                    {
                        fileExcluded = true;
                    }
                }

                // if all of the changes have been filtered out, then there is no changelist so we return null.
                if (changes.isEmpty() || (fileExcluded && noFilesInView(clientName, changes)))
                {
                    return null;
                }
            }
        }

        return new Changelist(revision, offset(date), user, comment, changes);
    }

    private long offset(Date date)
    {
        return date.getTime() + configuration.getTimeOffset() * Constants.MINUTE;
    }

    private boolean noFilesInView(String clientName, List<FileChange> changes) throws ScmException
    {
        Iterable<List<FileChange>> partitioned = Lists.partition(changes, FILE_LIMIT);
        for (List<FileChange> sublist: partitioned)
        {
            if (hasFilesInView(clientName, sublist))
            {
                return false;
            }
        }

        return true;
    }

    private boolean hasFilesInView(String clientName, List<FileChange> changes) throws ScmException
    {
        // p4 where <file1> <file2> ...
        // For every file in the view, a line goes to stdout.  For files not in
        // the view a line (<path> - file(s) not in client view) goes to
        // stderr.  Hence any line on stdout means there was a file in our
        // view.
        List<String> command = new ArrayList<String>(changes.size() + 4);
        command.add(getP4Command(COMMAND_WHERE));
        command.add(FLAG_CLIENT);
        command.add(clientName);
        command.add(COMMAND_WHERE);

        command.addAll(transform(changes, new Function<FileChange, String>()
        {
            public String apply(FileChange fileChange)
            {
                return fileChange.getPath();
            }
        }));

        final boolean[] stdoutSeen = new boolean[]{false};
        core.runP4WithHandler(new PerforceErrorDetectingFeedbackHandler(false)
        {
            public void handleStdout(String line)
            {
                stdoutSeen[0] = true;
            }
        }, null, command.toArray(new String[command.size()]));

        return stdoutSeen[0];
    }

    private FileChange getChangelistChange(String line) throws ScmException
    {
        // ... <depot file>#<revision> <action>
        Pattern re = Pattern.compile("\\.\\.\\. (.+)#([0-9]+) (.+)");
        Matcher matcher = re.matcher(line);

        if (matcher.matches())
        {
            return new FileChange(matcher.group(1), new Revision(matcher.group(2)), decodeAction(matcher.group(3)));
        }
        else
        {
            throw new ScmException("Could not parse affected file line from p4 describe '" + line + "'");
        }
    }

    public static FileChange.Action decodeAction(String action)
    {
        if (action.equals(ACTION_ADD) || action.equals(ACTION_ADDED_AS) || action.equals(ACTION_REFRESHING))
        {
            return FileChange.Action.ADD;
        }
        else if (action.equals(ACTION_BRANCH))
        {
            return FileChange.Action.BRANCH;
        }
        else if (action.equals(ACTION_DELETE) || action.equals(ACTION_DELETED_AS) || action.equals(ACTION_MOVE_DELETE))
        {
            return FileChange.Action.DELETE;
        }
        else if (action.equals(ACTION_EDIT) || action.equals(ACTION_UPDATING))
        {
            return FileChange.Action.EDIT;
        }
        else if (action.equals(ACTION_INTEGRATE))
        {
            return FileChange.Action.INTEGRATE;
        }
        else if (action.equals(ACTION_MOVE_ADD))
        {
            return FileChange.Action.MOVE;
        }
        else
        {
            return FileChange.Action.UNKNOWN;
        }
    }

    private Revision sync(ExecutionContext context, Revision revision, ScmFeedbackHandler handler, boolean cleanCheckout) throws ScmException
    {
        core.setContext(context);

        PerforceWorkspace workspace = workspaceManager.getSyncWorkspace(core, configuration, context);
        try
        {
            if (revision == null)
            {
                revision = getLatestRevision(workspace);
            }

            PerforceCheckoutFeedbackHandler perforceHandler = new PerforceCheckoutFeedbackHandler(false, handler);

            context.push();
            try
            {
                context.addString(PROPERTY_WORKSPACE, workspace.getName());
                context.addString(PROPERTY_REVISION, revision.getRevisionString());
                List<String[]> commands = PerforceConstants.resolveScript(context, cleanCheckout ? SCRIPT_CHECKOUT : SCRIPT_UPDATE);
                for (String[] command: commands)
                {
                    core.runP4WithHandler(perforceHandler, null, command);
                }
            }
            finally
            {
                context.pop();
            }
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }

        return revision;
    }

    private String getChangelistComment(String[] lines, int affectedFilesIndex)
    {
        String result = "";
        int i;

        for (i = 2; i < affectedFilesIndex - 1; i++)
        {
            if (result.length() > 0)
            {
                result += "\n";
            }

            if (lines[i].startsWith("\t"))
            {
                result += lines[i].substring(1);
            }
            else
            {
                result += lines[i];
            }
        }

        return result;
    }

    public PerforceClient(PerforceConfiguration configuration, PerforceWorkspaceManager workspaceManager) throws ScmException
    {
        this.configuration = configuration;
        this.workspaceManager = workspaceManager;

        core = new PerforceCore(configuration.getInactivityTimeout());
        if (StringUtils.stringSet(configuration.getPort()))
        {
            core.setEnv(ENV_PORT, configuration.getPort());
        }
        if (StringUtils.stringSet(configuration.getUser()))
        {
            core.setEnv(ENV_USER, configuration.getUser());
        }
        if (configuration.isUnicodeServer())
        {
            core.setEnv(ENV_CHARSET, configuration.getCharset());
        }

        String password = determinePassword(core, configuration);
        if (StringUtils.stringSet(password))
        {
            core.setEnv(ENV_PASSWORD, password);
        }
    }

    private String determinePassword(PerforceCore core, PerforceConfiguration configuration) throws ScmException
    {
        if (configuration.getUseTicketAuth())
        {
            PerforceCore.P4Result result = core.runP4(configuration.getPassword(), getP4Command(COMMAND_LOGIN), COMMAND_LOGIN, FLAG_DISPLAY_TICKET);
            String[] lines = core.splitLines(result);
            if (lines.length == 0)
            {
                throw new ScmException("No output from p4 login");
            }

            return lines[lines.length - 1];
        }
        else
        {
            return configuration.getPassword();
        }
    }

    public String getImplicitResource()
    {
        return RESOURCE_NAME;
    }

    public void init(ScmContext context, ScmFeedbackHandler handler) throws ScmException
    {
        // noop
    }

    public void destroy(ScmContext context, ScmFeedbackHandler handler) throws ScmException
    {
        // Clean up the persistent clients for this project.
        core.setContext(context.getEnvironmentContext());
        workspaceManager.cleanupPersistentWorkspaces(core, context, handler);
    }

    public void close()
    {
    }

    public Set<ScmCapability> getCapabilities(ScmContext context)
    {
        return EnumSet.allOf(ScmCapability.class);
    }

    public String getUid(ScmContext context)
    {
        return determinePort();
    }

    public String getLocation(ScmContext context)
    {
        return getUniqueWorkspaceString() + "@" + determinePort();
    }

    private String determinePort()
    {
        String port = configuration.getPort();
        if (port == null)
        {
            port = System.getenv(ENV_PORT);
            if (port == null)
            {
                // Some customers have a custom p4 binary that doesn't require a port to be
                // specified.
                port = "unknown";
            }
        }
        
        return port;
    }

    private String getUniqueWorkspaceString()
    {
        return configuration.getUseTemplateClient() ? configuration.getSpec() : SecurityUtils.sha1Digest(configuration.getView());
    }

    public List<ResourceProperty> getProperties(ExecutionContext context) throws ScmException
    {
        List<ResourceProperty> result = new LinkedList<ResourceProperty>();
        for (Map.Entry<String, String> entry : core.getEnv().entrySet())
        {
            result.add(new ResourceProperty(entry.getKey(), entry.getValue(), true, false));
        }

        result.add(new ResourceProperty("P4CLIENT", PerforceWorkspaceManager.getSyncWorkspaceName(configuration, context), true, false));
        return result;
    }

    public void testConnection(ScmContext context) throws ScmException
    {
        core.setContext(context.getEnvironmentContext());
        PerforceWorkspace workspace = workspaceManager.allocateWorkspace(core, configuration, context);
        try
        {
            if (!core.workspaceExists(workspace.getName()))
            {
                throw new ScmException("Client '" + workspace.getName() + "' does not exist");
            }
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    public Revision checkout(ExecutionContext context, Revision revision, ScmFeedbackHandler handler) throws ScmException
    {
        return sync(context, revision, handler, true);
    }

    public InputStream retrieve(ScmContext context, String path, Revision revision) throws ScmException
    {
        core.setContext(context.getEnvironmentContext());
        PerforceWorkspace workspace = workspaceManager.allocateWorkspace(core, configuration, context);
        try
        {
            File fullFile = new File(workspace.getRoot(), path);

            String fileArgument = fullFile.getAbsolutePath();
            if (revision != null)
            {
                fileArgument = fileArgument + "@" + revision;
            }

            PerforceCore.P4Result result = core.runP4(null, getP4Command("print"), FLAG_CLIENT, workspace.getName(), "print", "-q", fileArgument);
            return new ByteArrayInputStream(result.stdout.toString().getBytes("US-ASCII"));
        }
        catch (ScmException e)
        {
            if (e.getMessage().contains("no such file") || e.getMessage().contains("not in client view"))
            {
                String rev = revision == null ? "head" : revision.getRevisionString();
                throw new ScmException("File '" + path + "' revision " + rev + " does not exist in the client's view (" + e.getMessage() + ")");
            }
            else
            {
                throw e;
            }
        }
        catch (UnsupportedEncodingException e)
        {
            // Programmer error
            throw new ScmException(e);
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    public List<Changelist> getChanges(ScmContext context, Revision from, Revision to) throws ScmException
    {
        List<Changelist> result = new LinkedList<Changelist>();
        getRevisions(context, from, to, result);
        return result;
    }

    public List<Revision> getRevisions(ScmContext context, Revision from, Revision to) throws ScmException
    {
        return getRevisions(context, from, to, null);
    }

    private List<Revision> getRevisions(ScmContext scmContext, Revision from, Revision to, List<Changelist> changes) throws ScmException
    {
        List<Revision> result = new LinkedList<Revision>();

        core.setContext(scmContext.getEnvironmentContext());
        PerforceWorkspace workspace = workspaceManager.allocateWorkspace(core, configuration, scmContext);
        try
        {
            if (to == null)
            {
                to = getLatestRevision(workspace);
            }

            long fromNumber = getChangelistForRevision(workspace, from);
            long toNumber = getChangelistForRevision(workspace, to);

            if (fromNumber != toNumber)
            {
                boolean increasingRevisions = fromNumber < toNumber;

                String fromRevision;
                String toRevision;
                if (increasingRevisions)
                {
                    fromRevision = Long.toString(fromNumber + 1);
                    toRevision = Long.toString(toNumber);
                }
                else
                {
                    fromRevision = Long.toString(toNumber + 1);
                    toRevision = Long.toString(fromNumber);
                }

                PerforceCore.P4Result p4Result = core.runP4(null, getP4Command(COMMAND_CHANGES), FLAG_CLIENT, workspace.getName(), COMMAND_CHANGES, FLAG_STATUS, VALUE_SUBMITTED, "//" + workspace.getName() + "/...@" + fromRevision + "," + toRevision);
                Matcher matcher = PATTERN_CHANGES.matcher(p4Result.stdout);

                while (matcher.find())
                {
                    Revision revision = new Revision(matcher.group(1));
                    Changelist list = getChangelist(workspace.getName(), Long.valueOf(revision.toString()), changes != null);
                    if (list != null)
                    {
                        // P4 gives us the result in reverse order, so if the request revisions are
                        // increasing, we need to reverse the order of the revisions and the changelists.
                        
                        if (increasingRevisions)
                        {
                            result.add(0, revision);
                        }
                        else
                        {
                            result.add(revision);
                        }

                        if (changes != null)
                        {
                            if (increasingRevisions)
                            {
                                changes.add(0, list);
                            }
                            else
                            {
                                changes.add(list);
                            }
                        }
                    }
                }
            }

            return result;
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    private long getChangelistForRevision(PerforceWorkspace workspace, Revision revision) throws ScmException
    {
        try
        {
            // Optimisation - in this method we are not validating the
            // revision, if it is a number we assume it is good.
            return Long.parseLong(revision.toString());
        }
        catch(NumberFormatException e)
        {
            return mapRevisionToChangelist(workspace, revision);
        }
    }

    private long mapRevisionToChangelist(PerforceWorkspace workspace, Revision revision) throws ScmException
    {
        // It is a label, date or similar.  Get the highest changelist in
        // that revision spec, which is the best approximation we have.
        PerforceCore.P4Result p4Result = core.runP4(null, getP4Command(COMMAND_CHANGES), FLAG_CLIENT, workspace.getName(), COMMAND_CHANGES, FLAG_STATUS, VALUE_SUBMITTED, "//" + workspace.getName() + "/...@" + revision);
        Matcher matcher = PATTERN_CHANGES.matcher(p4Result.stdout);
        if (matcher.find())
        {
            return Long.parseLong(matcher.group(1));
        }
        else
        {
            throw new ScmException("No changelists found for revision '" + revision + "'");
        }
    }

    public Revision update(ExecutionContext context, Revision rev, ScmFeedbackHandler handler) throws ScmException
    {
        sync(context, rev, handler, false);
        return rev;
    }

    public void tag(ScmContext scmContext, Revision revision, String name, boolean moveExisting) throws ScmException
    {
        core.setContext(scmContext.getEnvironmentContext());

        PerforceWorkspace workspace = workspaceManager.allocateWorkspace(core, configuration, scmContext);
        try
        {
            if (!labelExists(workspace.getName(), name))
            {
                createLabel(workspace.getName(), name);
            }
            else if (!moveExisting)
            {
                throw new ScmException("Cannot create label '" + name + "': label already exists");
            }

            core.runP4(false, null, getP4Command(COMMAND_LABELSYNC), FLAG_CLIENT, workspace.getName(), COMMAND_LABELSYNC, FLAG_LABEL, name, "//" + workspace.getName() + "/...@" + revision.toString());
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    public void storeConnectionDetails(ExecutionContext context, File outputDir) throws ScmException, IOException
    {
        core.setContext(context);

        String clientName = PerforceWorkspaceManager.getSyncWorkspaceName(configuration, context);
        PerforceCore.P4Result result = core.runP4(null, getP4Command(COMMAND_INFO), FLAG_CLIENT, clientName, COMMAND_INFO);
        Files.write(result.stdout.toString(), new File(outputDir, "server-info.txt"), Charset.defaultCharset());

        result = core.runP4(null, getP4Command(COMMAND_CLIENT), FLAG_CLIENT, clientName, COMMAND_CLIENT, FLAG_OUTPUT);
        Files.write(result.stdout.toString(), new File(outputDir, "template-client.txt"), Charset.defaultCharset());
    }

    public EOLStyle getEOLPolicy(ExecutionContext context) throws ScmException
    {
        core.setContext(context);

        final EOLStyle[] eol = new EOLStyle[]{EOLStyle.NATIVE};

        PerforceWorkspace workspace = workspaceManager.getSyncWorkspace(core, configuration, context);
        try
        {
            core.runP4WithHandler(new PerforceErrorDetectingFeedbackHandler(true)
            {
                public void handleStdout(String line)
                {
                    if (line.startsWith("LineEnd:"))
                    {
                        String ending = line.substring(8).trim();
                        if (ending.equals("local"))
                        {
                            eol[0] = EOLStyle.NATIVE;
                        }
                        else if (ending.equals("unix") || ending.equals("share"))
                        {
                            eol[0] = EOLStyle.LINEFEED;
                        }
                        else if (ending.equals("mac"))
                        {
                            eol[0] = EOLStyle.CARRIAGE_RETURN;
                        }
                        else if (ending.equals("win"))
                        {
                            eol[0] = EOLStyle.CARRIAGE_RETURN_LINEFEED;
                        }
                    }
                }
            }, null, getP4Command(COMMAND_CLIENT), FLAG_CLIENT, workspace.getName(), COMMAND_CLIENT, FLAG_OUTPUT);
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }

        return eol[0];
    }

    public Revision parseRevision(ScmContext context, String revision) throws ScmException
    {
        core.setContext(context.getEnvironmentContext());
        PerforceWorkspace workspace = workspaceManager.allocateWorkspace(core, configuration, context);
        try
        {
            Revision candidateRevision = new Revision(revision);
            // See if we can reasonably convert this to a changelist,
            // implying it has some validity.
            long changelist = mapRevisionToChangelist(workspace, candidateRevision);
            if (isNumeric(revision))
            {
                // If the given value was numeric but the latest changelist it
                // maps to is different (quite possible as Perforce will take
                // non-existant numbers or those that do not exist in a
                // workspace's view), prefer what it maps to.
                candidateRevision = new Revision(changelist);
            }

            return candidateRevision;
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    private boolean isNumeric(String s)
    {
        try
        {
            Long.parseLong(s);
            return true;
        }
        catch(NumberFormatException e)
        {
            return false;
        }
    }

    public Revision getPreviousRevision(ScmContext context, Revision revision, boolean isFile) throws ScmException
    {
        try
        {
            return revision.calculatePreviousNumericalRevision();
        }
        catch (NumberFormatException e)
        {
            throw new ScmException("Invalid revision '" + revision.getRevisionString() + "': " + e.getMessage());
        }
    }

    public String getEmailAddress(ScmContext context, String user) throws ScmException
    {
        core.setContext(context.getEnvironmentContext());
        try
        {
            PerforceCore.P4Result p4Result = core.runP4(null, getP4Command(COMMAND_USER), COMMAND_USER, FLAG_OUTPUT, user);
            Matcher matcher = PATTERN_EMAIL.matcher(p4Result.stdout);
            if (matcher.find())
            {
                return matcher.group(1);
            }
            else
            {
                return null;
            }
        }
        catch (ScmException e)
        {
            // User may not exist.
            return null;
        }
    }

    public boolean configChangeRequiresClean(ScmConfiguration oldConfig, ScmConfiguration newConfig)
    {
        PerforceConfiguration oldP4 = (PerforceConfiguration) oldConfig;
        PerforceConfiguration newP4 = (PerforceConfiguration) newConfig;
        return !Objects.equal(oldP4.getPort(), newP4.getPort()) ||
                oldP4.getUseTemplateClient() != newP4.getUseTemplateClient() ||
                !Objects.equal(oldP4.getSpec(), newP4.getSpec()) ||
                !Objects.equal(oldP4.getView(), newP4.getView()) ||
                !Objects.equal(oldP4.getOptions(), newP4.getOptions()) ||
                !Objects.equal(oldP4.getStream(), newP4.getStream()) ||
                !Objects.equal(oldP4.getSyncWorkspacePattern(), newP4.getSyncWorkspacePattern());
    }

    public boolean labelExists(String client, String name) throws ScmException
    {
        PerforceCore.P4Result p4Result = this.core.runP4(null, getP4Command(COMMAND_LABELS), FLAG_CLIENT, client, COMMAND_LABELS);

        // $ p4 labels
        // Label jim 2006/06/20 'Created by Jason. '
        Pattern splitter = Pattern.compile("^Label (.+) [0-9/]+ '.*'$", Pattern.MULTILINE);
        Matcher matcher = splitter.matcher(p4Result.stdout);
        while (matcher.find())
        {
            if (matcher.group(1).equals(name))
            {
                return true;
            }
        }

        return false;
    }

    private void createLabel(String client, String name) throws ScmException
    {
        PerforceCore.P4Result p4Result = this.core.runP4(null, getP4Command(COMMAND_LABEL), FLAG_CLIENT, client, COMMAND_LABEL, FLAG_OUTPUT, name);
        this.core.runP4(p4Result.stdout.toString(), getP4Command(COMMAND_LABEL), FLAG_CLIENT, client, COMMAND_LABEL, FLAG_INPUT);
    }

    public static void main(String argv[])
    {
        try
        {
            PerforceClient client = new PerforceClient(new PerforceConfiguration("localhost:1666", "jsankey", "", "pulse-demo"), new PerforceWorkspaceManager());
            client.retrieve(null, "file", new Revision("2"));
            List<Changelist> cls = client.getChanges(null, new Revision("2"), new Revision("6"));

            for (Changelist l : cls)
            {
                System.out.println("Changelist:");
                System.out.println("  Revision: " + l.getRevision());
                System.out.println("  Date    : " + new Date(l.getTime()));
                System.out.println("  Author  : " + l.getAuthor());
                System.out.println("  Comment : " + l.getComment());
                System.out.println("  Files   : " + l.getRevision());

                for (FileChange c : l.getChanges())
                {
                    System.out.println("    " + c.getPath() + "#" + c.getRevision().getRevisionString() + " - " + c.getAction());
                }
            }
        }
        catch (ScmException e)
        {
            e.printStackTrace();
        }
    }

    public void beforePatch(ExecutionContext context, List<FileStatus> statuses) throws ScmException
    {
        core.setContext(context);

        Predicate<FileStatus> addedPredicate = new Predicate<FileStatus>()
        {
            public boolean apply(FileStatus fileStatus)
            {
                FileStatus.State state = fileStatus.getState();
                return state == FileStatus.State.ADDED ||
                        state == FileStatus.State.BRANCHED || 
                        state == FileStatus.State.RENAMED;
            }
        };
        
        Iterable<FileStatus> addedFiles = Iterables.filter(statuses, addedPredicate);
        Iterable<FileStatus> existingFiles = Iterables.filter(statuses, Predicates.not(addedPredicate));
        
        PerforceWorkspace workspace = workspaceManager.getSyncWorkspace(core, configuration, context);
        try
        {
            Iterable<List<FileStatus>> partitioned = Iterables.partition(addedFiles, FILE_LIMIT);
            for (List<FileStatus> sublist: partitioned)
            {
                convertTargetPathsForAddedFiles(workspace, sublist);
            }

            partitioned = Iterables.partition(existingFiles, FILE_LIMIT);
            for (List<FileStatus> sublist: partitioned)
            {
                convertTargetPathsForExistingFiles(workspace, sublist);
            }
        }
        finally
        {
            workspaceManager.freeWorkspace(core, workspace);
        }
    }

    private void convertTargetPathsForAddedFiles(PerforceWorkspace workspace, List<FileStatus> statuses) throws ScmException
    {
        List<String> whereCommand = new LinkedList<String>();
        whereCommand.add(getP4Command(COMMAND_WHERE));
        whereCommand.add(FLAG_CLIENT);
        whereCommand.add(workspace.getName());
        whereCommand.add(COMMAND_WHERE);

        if (addFilesToMapToCommand(statuses, whereCommand))
        {
            final Map<String, String> mappedPaths = new HashMap<String, String>();
            core.runP4WithHandler(new PerforceErrorDetectingFeedbackHandler(true)
            {
                public void handleStdout(String line)
                {
                    if (line.startsWith("//"))
                    {
                        String[] parts = line.split("\\s+");
                        if (parts.length == 3)
                        {
                            mappedPaths.put(parts[0], PerforceCore.stripClientPrefix(parts[1]));
                        }
                    }
                }
            }, null, whereCommand.toArray(new String[whereCommand.size()]));
            
            mapFiles(statuses, mappedPaths);
        }
    }

    private void convertTargetPathsForExistingFiles(PerforceWorkspace workspace, List<FileStatus> statuses) throws ScmException
    {
        List<String> fstatCommand = new LinkedList<String>();
        fstatCommand.add(getP4Command(COMMAND_FSTAT));
        fstatCommand.add(FLAG_CLIENT);
        fstatCommand.add(workspace.getName());
        fstatCommand.add(COMMAND_FSTAT);
        fstatCommand.add(FLAG_PATH_IN_DEPOT_FORMAT);

        if (addFilesToMapToCommand(statuses, fstatCommand))
        {
            final Map<String, String> mappedPaths = new HashMap<String, String>();
            core.runP4WithHandler(new AbstractPerforceFStatFeedbackHandler()
            {
                @Override
                protected void handleCurrentItem()
                {
                    String depotFile = currentItem.get("depotFile");
                    String path = currentItem.get("clientFile");
                    if (StringUtils.stringSet(depotFile) && StringUtils.stringSet(path))
                    {
                        mappedPaths.put(depotFile, PerforceCore.stripClientPrefix(path));
                    }
                }
            }, null, fstatCommand.toArray(new String[fstatCommand.size()]));
            
            mapFiles(statuses, mappedPaths);
        }
    }

    private boolean addFilesToMapToCommand(List<FileStatus> statuses, List<String> command)
    {
        boolean fileToMap = false;
        for (FileStatus status: statuses)
        {
            String targetPath = status.getTargetPath();
            if (StringUtils.stringSet(targetPath) && targetPath.startsWith("//"))
            {
                fileToMap = true;
                command.add(targetPath);
            }
        }
        return fileToMap;
    }

    private void mapFiles(List<FileStatus> statuses, Map<String, String> mappedPaths) throws ScmException
    {
        for (FileStatus status: statuses)
        {
            String mapped = mappedPaths.get(status.getTargetPath());
            if (mapped != null)
            {
                status.setTargetPath(mapped);
            }
        }
    }

    public void afterPatch(ExecutionContext context, List<FileStatus> statuses)
    {
        // Do nothing.
    }

    PerforceConfiguration getConfiguration()
    {
        return configuration;
    }
}
