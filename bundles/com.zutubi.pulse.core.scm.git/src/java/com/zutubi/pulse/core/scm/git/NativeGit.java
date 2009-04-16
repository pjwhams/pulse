package com.zutubi.pulse.core.scm.git;

import com.zutubi.pulse.core.scm.api.Revision;
import com.zutubi.pulse.core.scm.api.ScmCancelledException;
import com.zutubi.pulse.core.scm.api.ScmFeedbackHandler;
import static com.zutubi.pulse.core.scm.git.GitConstants.*;
import com.zutubi.pulse.core.util.process.AsyncProcess;
import com.zutubi.pulse.core.util.process.LineHandler;
import com.zutubi.util.StringUtils;
import com.zutubi.util.logging.Logger;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The native git object is a wrapper around the implementation details for running native git operations.
 */
public class NativeGit
{
    private static final Logger LOG = Logger.getLogger(NativeGit.class);

    /**
     * Sentinal value used to detect separate parts of log entries.  A hash is
     * chosen as it is difficult to have one in a commit comment (it is a
     * comment character).
     */
    private static final String LOG_SENTINAL = "#";

    private ProcessBuilder git;

    public NativeGit()
    {
        git = new ProcessBuilder();
    }

    /**
     * Set the working directory in which the native git commands will be run.
     *
     * @param dir working directory must exist.
     */
    public void setWorkingDirectory(File dir)
    {
        if (dir == null || !dir.isDirectory())
        {
            throw new IllegalArgumentException("The working directory must be an existing directory.");
        }
        git.directory(dir);
    }

    public void clone(ScmFeedbackHandler handler, String repository, String dir) throws GitException
    {
        run(handler, getGitCommand(), COMMAND_CLONE, FLAG_NO_CHECKOUT, repository, dir);
    }

    public void pull(ScmFeedbackHandler handler) throws GitException
    {
        run(handler, getGitCommand(), COMMAND_PULL);
    }

    public InputStream show(String file) throws GitException
    {
        return show(REVISION_HEAD, file);
    }

    public InputStream show(String revision, String file) throws GitException
    {
        String[] commands = {getGitCommand(), COMMAND_SHOW, revision + ":" + file};

        final StringBuffer buffer = new StringBuffer();
        OutputHandlerAdapter handler = new OutputHandlerAdapter()
        {
            public void handleStdout(String line)
            {
                buffer.append(line);
            }
        };

        runWithHandler(handler, null, commands);

        return new ByteArrayInputStream(buffer.toString().getBytes());
    }

    public List<GitLogEntry> log() throws GitException
    {
        return log(null, null, -1);
    }

    public List<GitLogEntry> log(int changes) throws GitException
    {
        return log(null, null, changes);
    }

    public List<GitLogEntry> log(String from, String to) throws GitException
    {
        return log(from, to, -1);
    }

    public List<GitLogEntry> log(String from, String to, int changes) throws GitException
    {
        List<String> command = new LinkedList<String>();
        command.add(getGitCommand());
        command.add(COMMAND_LOG);
        command.add(FLAG_NAME_STATUS);
        command.add(FLAG_SHOW_MERGE_FILES);
        command.add(FLAG_PRETTY + "=format:" + LOG_SENTINAL + "%n%H%n%cn%n%cd%n%s%n%b" + LOG_SENTINAL);
        command.add(FLAG_REVERSE);
        if (changes != -1)
        {
            command.add(FLAG_CHANGES);
            command.add(Integer.toString(changes));
        }
        if (from != null && to != null)
        {
            command.add(from + ".." + to);
        }

        LogOutputHandler handler = new LogOutputHandler();

        runWithHandler(handler, null, command.toArray(new String[command.size()]));

        return handler.getEntries();
    }

    public void checkout(ScmFeedbackHandler handler, String branch) throws GitException
    {
        run(handler, getGitCommand(), COMMAND_CHECKOUT, branch);
    }

    public void checkout(ScmFeedbackHandler handler, String branch, String localBranch) throws GitException
    {
        run(handler, getGitCommand(), COMMAND_CHECKOUT, FLAG_BRANCH, localBranch, branch);
    }

    public void deleteBranch(String branch) throws GitException
    {
        run(getGitCommand(), COMMAND_BRANCH, FLAG_DELETE, branch);
    }

    public void cretaeBranch(String branch) throws GitException
    {
        run(getGitCommand(), COMMAND_BRANCH, branch);
    }

    public List<GitBranchEntry> branch() throws GitException
    {
        String[] command = {getGitCommand(), COMMAND_BRANCH};

        BranchOutputHandler handler = new BranchOutputHandler();

        runWithHandler(handler, null, command);

        return handler.getBranches();
    }

    /**
     * The purpose of this diff implementation is purely for generating a set of status messages
     * that identify the difference added by the specified revision
     *
     * @param handler the handler that will be recieving the status messages
     * @param revision the revision of interest.  If null, HEAD is used.
     *
     * @throws GitException is thrown if there is an error
     */
    public void diff(ScmFeedbackHandler handler, Revision revision) throws GitException
    {
        String rev = (revision != null) ? revision.getRevisionString() : "HEAD";
        diff(handler, new Revision(rev + "~1"), new Revision(rev));
    }

    public void diff(ScmFeedbackHandler handler, Revision revA, Revision revB) throws GitException
    {
        String[] command = {getGitCommand(), COMMAND_DIFF, FLAG_NAME_STATUS, revA.getRevisionString(), revB.getRevisionString()};
        run(handler, command);
    }

    protected void run(String... commands) throws GitException
    {
        run(null, commands);
    }

    protected void run(ScmFeedbackHandler scmHandler, String... commands) throws GitException
    {
        OutputHandlerAdapter handler = new OutputHandlerAdapter(scmHandler);

        runWithHandler(handler, null, commands);
    }

    protected void runWithHandler(final OutputHandler handler, String input, String... commands) throws GitException
    {
        String commandLine = StringUtils.join(" ", commands);
        handler.handleCommandLine(commandLine);

        Process child;

        git.command(commands);

        try
        {
            child = git.start();
        }
        catch (IOException e)
        {
            throw new GitException("Could not start git process: " + e.getMessage(), e);
        }

        if (input != null)
        {
            try
            {
                OutputStream stdinStream = child.getOutputStream();

                stdinStream.write(input.getBytes());
                stdinStream.close();
            }
            catch (IOException e)
            {
                throw new GitException("Error writing to input of git process", e);
            }
        }

        AsyncProcess async = new AsyncProcess(child, new LineHandler()
        {
            public void handle(String line, boolean error)
            {
                if (error)
                {
                    handler.handleStderr(line);
                }
                else
                {
                    handler.handleStdout(line);
                }
            }
        }, true);

        try
        {
            Integer exitCode;
            do
            {
                handler.checkCancelled();
                exitCode = async.waitFor(10, TimeUnit.SECONDS);
            }
            while (exitCode == null);

            handler.handleExitCode(exitCode);

            if (exitCode != 0)
            {
                throw new GitException("Git command: " + commandLine + " exited " +
                        "with non zero exit code: " + handler.getExitCode() + ".");
            }
        }
        catch (InterruptedException e)
        {
            // Do nothing
        }
        catch (IOException e)
        {
            throw new GitException("Error reading output of git process", e);
        }
        finally
        {
            async.destroy();
        }
    }

    protected String getGitCommand()
    {
        return System.getProperty(PROPERTY_GIT_COMMAND, DEFAULT_GIT);
    }

    interface OutputHandler
    {
        void handleCommandLine(String line);

        void handleStdout(String line);

        void handleStderr(String line);

        void handleExitCode(int code) throws GitException;

        int getExitCode();

        void checkCancelled() throws GitOperationCancelledException;
    }

    private static class OutputHandlerAdapter implements OutputHandler
    {
        private int exitCode;
        private ScmFeedbackHandler scmHandler;

        public OutputHandlerAdapter()
        {
        }

        public OutputHandlerAdapter(ScmFeedbackHandler scmHandler)
        {
            this.scmHandler = scmHandler;
        }

        public void handleCommandLine(String line)
        {
            if (scmHandler != null)
            {
                scmHandler.status(">> " + line);
            }
        }

        public void handleStdout(String line)
        {
            if (scmHandler != null)
            {
                scmHandler.status(line);
            }
        }

        public void handleStderr(String line)
        {
            if (scmHandler != null)
            {
                scmHandler.status(line);
            }
        }

        public void handleExitCode(int code)
        {
            this.exitCode = code;
        }

        public int getExitCode()
        {
            return exitCode;
        }

        public void checkCancelled() throws GitOperationCancelledException
        {
            if (scmHandler != null)
            {
                try
                {
                    scmHandler.checkCancelled();
                }
                catch (ScmCancelledException e)
                {
                    throw new GitOperationCancelledException(e);
                }
            }
        }
    }

    /**
     * Read the output from the git log command, interpretting the output.
     * <p/>
     *        #
     *        e34da05e88de03a4aa5b10b338382f09bbe65d4b
     *        Daniel Ostermeier
     *        Sun Sep 28 15:06:49 2008 +1000
     *        removed content from a.txt
     *        #
     *        M       a.txt
     *
     * This format is generated using --pretty=format:... (see {@link NativeGit#log})
     */
    static class LogOutputHandler extends OutputHandlerAdapter
    {
        /**
         * The date format used to read the 'date' field on git log output.
         */
        private final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");

        private List<GitLogEntry> entries;

        private List<String> lines;

        public LogOutputHandler()
        {
            lines = new LinkedList<String>();
        }

        public void handleStdout(String line)
        {
            lines.add(line);
        }

        /**
         * Get the list of git log entries.  Note that the entries are parsed from the log output
         * when this method is called, so ensure that you wait until the log command is complete
         * before requesting the entries.
         *
         * @return the list of parsed git log entries.
         *
         * @throws GitException if there is a problem parsing the log output.
         */
        public List<GitLogEntry> getEntries() throws GitException
        {
            try
            {
                if (entries == null)
                {
                    entries = new LinkedList<GitLogEntry>();

                    Iterator<String> i = lines.iterator();

                    // Skip up to and including initial sentinal
                    while (i.hasNext())
                    {
                        if (i.next().equals(LOG_SENTINAL))
                        {
                            break;
                        }
                    }
                    
                    while (i.hasNext())
                    {
                        GitLogEntry logEntry = new GitLogEntry();
                        List<String> raw = new LinkedList<String>();
                        String str;
                        raw.add(str = i.next());
                        logEntry.setId(str);
                        raw.add(str = i.next());
                        logEntry.setAuthor(str);
                        raw.add(str = i.next());
                        logEntry.setDateString(str);
                        try
                        {
                            logEntry.setDate(LOG_DATE_FORMAT.parse(str));
                        }
                        catch (ParseException e)
                        {
                            LOG.severe("Failed to parse the date: '" + str + "'");
                        }

                        String comment = "";
                        while (i.hasNext() && !(str = i.next()).equals(LOG_SENTINAL))
                        {
                            if (comment.length() > 0)
                            {
                                comment += "\n";
                            }
                            comment += str;
                            raw.add(str);
                        }
                        logEntry.setComment(comment);
                        raw.add(str); // blank line

                        // Until sentinal or until the end.  Note that most of
                        // the time a blank line appears at the end of the
                        // files, but we use the sentinal as a more reliable
                        // way to detect termination.
                        while (i.hasNext() && !(str = i.next()).equals(LOG_SENTINAL))
                        {
                            String[] parts = str.split("\\s+", 2);
                            if (parts.length == 2)
                            {
                                logEntry.addFileChange(parts[1], parts[0]);
                            }
                            raw.add(str);
                        }

                        logEntry.setRaw(raw);
                        entries.add(logEntry);
                    }
                }
                return entries;
            }
            catch (Exception e)
            {
                // print some debugging output.
                LOG.severe("A problem has occured whilst parsing the git log output.");
                LOG.severe(e);
                LOG.severe("The log output received was:\n" + StringUtils.join("\n", lines));

                throw new GitException(e);
            }
        }
    }

    /**
     * Read the output from the git branch command, interpretting the information as
     * necessary.
     */
    private class BranchOutputHandler extends OutputHandlerAdapter
    {
        private List<GitBranchEntry> branches = new LinkedList<GitBranchEntry>();

        private final Pattern BRANCH_OUTPUT = Pattern.compile("\\*?\\s+(.+)");

        public void handleStdout(String line)
        {
            Matcher matcher = BRANCH_OUTPUT.matcher(line);
            if (matcher.matches())
            {
                GitBranchEntry entry = new GitBranchEntry(line.startsWith("*"), matcher.group(1).trim());
                branches.add(entry);
            }
        }

        public List<GitBranchEntry> getBranches()
        {
            return branches;
        }
    }

    /**
     * Provide command line style access to running git commands for testing.
     * @param argv command line arguments
     * @throws IOException if an error occurs.
     */
    public static void main(String... argv) throws IOException
    {
        if (argv.length == 0)
        {
            System.out.println("Please enter the full git command you with to execute.");
            return;
        }

        OutputHandlerAdapter outputHandler = new OutputHandlerAdapter()
        {
            public void handleStdout(String line)
            {
                System.out.println(line);
            }

            public void handleStderr(String line)
            {
                System.err.println(line);
            }
        };

        try
        {
            NativeGit git = new NativeGit();
            git.setWorkingDirectory(new File("."));
            System.out.println(new File(".").getCanonicalPath());
            if (!Boolean.getBoolean("skip.env"))
            {
                // use a tree map to provide ordering to the keys.
                System.out.println("========= Execution Environment ============");
                Map<String, String> env = new TreeMap<String, String>(git.git.environment());
                for (String key : env.keySet())
                {
                    String value = env.get(key);
                    System.out.println(key + "=" + value);
                }
                System.out.println();
                System.out.println("========= Command output ============");
                System.out.println(StringUtils.join(" ", argv));
            }
            git.runWithHandler(outputHandler, null, argv);
        }
        catch (GitException e)
        {
            System.out.println("Exit Status: " + outputHandler.getExitCode());
            e.printStackTrace();
        }
    }
}
