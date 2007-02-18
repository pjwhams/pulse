package com.zutubi.pulse.scm.p4;

import com.zutubi.pulse.core.model.*;
import com.zutubi.pulse.filesystem.remote.RemoteFile;
import com.zutubi.pulse.scm.SCMChangeAccumulator;
import com.zutubi.pulse.scm.SCMException;
import com.zutubi.pulse.test.PulseTestCase;
import com.zutubi.pulse.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 */
public class P4ServerTest extends PulseTestCase
{
    private P4Client client;
    private P4Server server;
    private File tmpDir;
    private File repoDir;
    private File workDir;
    private Process p4dProcess;
    private boolean generateMode = false;

    protected void setUp() throws Exception
    {
        super.setUp();
        client = new P4Client();

        tmpDir = FileSystemUtils.createTempDir(getClass().getName(), "");
        repoDir = new File(tmpDir, "repo");

        File repoZip = getTestDataFile("server-core", "repo", "zip");
        FileSystemUtils.extractZip(repoZip, repoDir);

        // Restore from checkpoint
        p4dProcess = Runtime.getRuntime().exec(new String[] { "p4d", "-r", repoDir.getAbsolutePath(), "-jr", "checkpoint.1"});
        p4dProcess.waitFor();

        p4dProcess = Runtime.getRuntime().exec(new String[] { "p4d", "-r", repoDir.getAbsolutePath(), "-p", "6666"});

        workDir = new File(tmpDir, "work");
        workDir.mkdirs();

        waitForServer(6666);
    }

    protected void tearDown() throws Exception
    {
        client = null;
        server = null;

        p4dProcess.destroy();
        Thread.sleep(400);
        removeDirectory(tmpDir);
        super.tearDown();
    }

    public void testGetLocation()
    {
        getServer("test-client");
        assertEquals(server.getLocation(), "test-client@:6666");
    }

    public void testGetLatestRevision() throws SCMException
    {
        getServer("test-client");
        assertEquals(8, server.getLatestRevision().getRevisionNumber());
    }

    public void testCheckoutHead() throws Exception
    {
        getServer("depot-client");
        List<Change> changes = checkoutChanges(null, workDir, null, 8);

        assertEquals(10, changes.size());
        for (int i = 0; i < 10; i++)
        {
            Change change = changes.get(i);
            assertEquals(Change.Action.ADD, change.getAction());

            // Foolish of me to create file10 which ruins lexical ordering :|
            int number;
            if (i == 0)
            {
                number = 1;
            }
            else if (i == 1)
            {
                number = 10;
            }
            else
            {
                number = i;
            }

            assertEquals(String.format("//depot/file%d", number), change.getFilename());
        }

        checkDirectory("checkoutHead");
    }

    public void testCheckoutRevision() throws Exception
    {
        getServer("depot-client");
        NumericalRevision revision = (NumericalRevision) server.checkout(null, workDir, new NumericalRevision(1), null);
        assertEquals(1, revision.getRevisionNumber());
        checkDirectory("checkoutRevision");
    }

    public void testCheckoutFile() throws SCMException
    {
        getServer("depot-client");
        String content = server.checkout(null, FileSystemUtils.composeFilename("depot", "file2"));
        assertEquals("content of file2: edited at the same time as file2 in depot2.\n", content);
    }

    public void testCheckoutFileRevision() throws SCMException
    {
        getServer("depot-client");
        String content = server.checkout(new NumericalRevision(2), FileSystemUtils.composeFilename("depot", "file2"));
        assertEquals("content of file2\n", content);
    }

    public void testHasChangedSince() throws Exception
    {
        getServer("test-client");
        assertTrue(server.hasChangedSince(new NumericalRevision(6)));
    }

    public void testHasChangedSinceRestrictedToView() throws Exception
    {
        getServer("depot-client");
        assertFalse(server.hasChangedSince(new NumericalRevision(6)));
    }

    public void testHasChangeSinceExcluded() throws Exception
    {
        getServer("test-client");
        server.setExcludedPaths(Arrays.asList("//depot2/**"));
        assertFalse(server.hasChangedSince(new NumericalRevision(6)));
    }

    public void testHasChangeSinceSomeExcluded() throws Exception
    {
        getServer("test-client");
        server.setExcludedPaths(Arrays.asList("//depot2/**"));
        assertTrue(server.hasChangedSince(new NumericalRevision(3)));
    }

    public void testHasChangeNoneExcluded() throws Exception
    {
        getServer("test-client");
        server.setExcludedPaths(Arrays.asList("//depot1/**"));
        assertTrue(server.hasChangedSince(new NumericalRevision(6)));
    }

    public void testGetChanges() throws Exception
    {
        // [{ uid: :6666, rev: 7, changes: [//depot2/test-branch/file9#2 - INTEGRATE] },
        //  { uid: :6666, rev: 6, changes: [//depot2/file9#2 - EDIT] },
        //  { uid: :6666, rev: 5, changes: [//depot2/test-branch/file1#1 - BRANCH, //depot2/test-branch/file2#1 - BRANCH, //depot2/test-branch/file3#1 - BRANCH, //depot2/test-branch/file4#1 - BRANCH, //depot2/test-branch/file5#1 - BRANCH, //depot2/test-branch/file6#1 - BRANCH, //depot2/test-branch/file7#1 - BRANCH, //depot2/test-branch/file8#1 - BRANCH, //depot2/test-branch/file9#1 - BRANCH] },
        //  { uid: :6666, rev: 4, changes: [//depot/file2#2 - EDIT, //depot2/file2#2 - EDIT] },
        //  { uid: :6666, rev: 3, changes: [//depot2/file1#2 - EDIT, //depot2/file10#2 - DELETE] },
        //  { uid: :6666, rev: 2, changes: [//depot2/file1#1 - ADD, //depot2/file10#1 - ADD, //depot2/file2#1 - ADD, //depot2/file3#1 - ADD, //depot2/file4#1 - ADD, //depot2/file5#1 - ADD, //depot2/file6#1 - ADD, //depot2/file7#1 - ADD, //depot2/file8#1 - ADD, //depot2/file9#1 - ADD] }]
        getServer("test-client");
        List<Changelist> changes = server.getChanges(new NumericalRevision(1), new NumericalRevision(7), "");
        assertEquals(6, changes.size());
        Changelist list = changes.get(1);
        assertEquals("Delete and edit files in depot2.", list.getComment());
        assertEquals("test-user", list.getUser());
        assertEquals(3, ((NumericalRevision) list.getRevision()).getRevisionNumber());
        List<Change> changedFiles = list.getChanges();
        assertEquals(2, changedFiles.size());
        Change file1 = changedFiles.get(0);
        assertEquals("//depot2/file1", file1.getFilename());
        assertEquals(Change.Action.EDIT, file1.getAction());
        Change file10 = changedFiles.get(1);
        assertEquals("//depot2/file10", file10.getFilename());
        assertEquals(Change.Action.DELETE, file10.getAction());
    }

    public void testGetChangesRestrictedToView() throws Exception
    {
        getServer("depot-client");
        List<Changelist> changes = server.getChanges(new NumericalRevision(1), new NumericalRevision(7), "");
        assertEquals(1, changes.size());
        assertEquals(4, ((NumericalRevision) changes.get(0).getRevision()).getRevisionNumber());
    }

    public void testListNonExistent() throws SCMException
    {
        getServer("test-client");
        try
        {
            server.getListing("depot4");
            fail();
        }
        catch (SCMException e)
        {
            assertTrue(e.getMessage().contains("does not exist"));
        }
    }

    public void testListRoot() throws SCMException
    {
        getServer("test-client");
        List<RemoteFile> files = server.getListing("");
        assertEquals(2, files.size());
        RemoteFile f = files.get(0);
        assertEquals("depot", f.getName());
        assertTrue(f.isDirectory());

        f = files.get(1);
        assertEquals("depot2", f.getName());
        assertEquals("depot2", f.getPath());
        assertTrue(f.isDirectory());
    }

    public void testListPath() throws SCMException
    {
        getServer("test-client");
        List<RemoteFile> files = server.getListing("depot2");
        assertEquals(10, files.size());

        RemoteFile f;
        for (int i = 0; i < 9; i++)
        {
            f = files.get(i);
            assertTrue(f.isFile());
            assertEquals("file" + (i + 1), f.getName());
            assertEquals("depot2/file" + (i + 1), f.getPath());
            assertEquals("text/plain", f.getMimeType());
        }

        f = files.get(9);
        assertEquals("test-branch", f.getName());
        assertEquals("depot2/test-branch", f.getPath());
        assertTrue(f.isDirectory());
    }

    public void testListComplexClient() throws SCMException
    {
        getServer("complex-client");
        List<RemoteFile> files = server.getListing("");
        assertEquals(1, files.size());
        RemoteFile remoteFile = files.get(0);
        assertEquals("src", remoteFile.getName());
        assertTrue(remoteFile.isDirectory());
    }

    public void testListComplexSrc() throws SCMException
    {
        getServer("complex-client");
        List<RemoteFile> files = server.getListing("src");
        assertEquals(2, files.size());

        RemoteFile remoteFile = files.get(0);
        assertEquals("host", remoteFile.getName());
        assertTrue(remoteFile.isDirectory());

        remoteFile = files.get(1);
        assertEquals("libraries", remoteFile.getName());
        assertTrue(remoteFile.isDirectory());
    }

    public void testListComplexSnuth() throws SCMException
    {
        getServer("complex-client");
        List<RemoteFile> files = server.getListing("src/libraries/snuth");
        assertEquals(2, files.size());

        RemoteFile remoteFile = files.get(0);
        assertEquals("Makefile", remoteFile.getName());
        assertFalse(remoteFile.isDirectory());

        remoteFile = files.get(1);
        assertEquals("source.c", remoteFile.getName());
        assertFalse(remoteFile.isDirectory());
    }

    public void testTag() throws SCMException
    {
        getServer("test-client");
        assertFalse(server.labelExists("test-client", "test-tag"));
        server.tag(new NumericalRevision(5), "test-tag", false);
        assertTrue(server.labelExists("test-client", "test-tag"));
        P4Client.P4Result result = client.runP4(null, "p4", "-c", "test-client", "sync", "-f", "-n", "@test-tag");
        assertTrue(result.stdout.toString().contains("//depot2/file9#1"));
    }

    public void testMoveTag() throws SCMException
    {
        testTag();
        server.tag(new NumericalRevision(7), "test-tag", true);
        assertTrue(server.labelExists("test-client", "test-tag"));
        P4Client.P4Result result = client.runP4(null, "p4", "-c", "test-client", "sync", "-f", "-n", "@test-tag");
        assertTrue(result.stdout.toString().contains("//depot2/file9#2"));
    }

    public void testUnmovableTag() throws SCMException
    {
        getServer("test-client");
        server.tag(new NumericalRevision(5), "test-tag", false);
        assertTrue(server.labelExists("test-client", "test-tag"));
        try
        {
            server.tag(new NumericalRevision(7), "test-tag", false);
            fail();
        }
        catch(SCMException e)
        {
            assertEquals(e.getMessage(), "Cannot create label 'test-tag': label already exists");
        }
    }

    public void testTagSameRevision() throws SCMException
    {
        getServer("test-client");
        server.tag(new NumericalRevision(5), "test-tag", false);
        assertTrue(server.labelExists("test-client", "test-tag"));
        server.tag(new NumericalRevision(5), "test-tag", true);
    }

    public void testGetRevisionsSince() throws SCMException
    {
        getServer("test-client");
        List<Revision> revisions = server.getRevisionsSince(new NumericalRevision(5));
        assertEquals(2, revisions.size());
        assertEquals("6", revisions.get(0).getRevisionString());
        assertEquals("7", revisions.get(1).getRevisionString());
    }

    public void testGetRevisionsSinceLatest() throws SCMException
    {
        getServer("test-client");
        List<Revision> revisions = server.getRevisionsSince(new NumericalRevision(7));
        assertEquals(0, revisions.size());
    }

    public void testCheckoutThenUpdate() throws SCMException, IOException
    {
        getServer("depot-client");
        NumericalRevision coRevision = new NumericalRevision(1);
        NumericalRevision got = (NumericalRevision) server.checkout("my-id", workDir, coRevision, null);
        assertEquals(1, got.getRevisionNumber());
        checkDirectory("checkoutRevision");

        NumericalRevision updateRevision = new NumericalRevision(8);
        List<Change> changes = updateChanges("my-id", workDir, updateRevision);
        checkDirectory("checkoutHead");
        assertEquals(1, changes.size());
        Change change = changes.get(0);
        assertEquals("//depot/file2", change.getFilename());
        assertEquals(Change.Action.EDIT, change.getAction());
    }

    public void testUpdateSameRevision() throws SCMException, IOException
    {
        getServer("depot-client");
        server.checkout("my-id", workDir, null, null);

        List<Change> changes = updateChanges("my-id", workDir, null);
        checkDirectory("checkoutHead");
        assertEquals(0, changes.size());
    }

    public void testMultiUpdates() throws SCMException, IOException
    {
        getServer("test-client");

        NumericalRevision coRevision = new NumericalRevision(1);
        server.checkout("my-id", workDir, coRevision, null);

        for(int i = 2; i <= 8; i++)
        {
            NumericalRevision updateRevision = new NumericalRevision(i);
            updateChanges("my-id", workDir, updateRevision);
        }
    }

    public void testGetFileRevision() throws SCMException
    {
        getServer("test-client");
        FileRevision rev = server.getFileRevision("//depot2/file1", new NumericalRevision(5));
        assertTrue(rev instanceof NumericalFileRevision);
        assertEquals("2", rev.getRevisionString());
    }

    public void testGetFileRevisionUnknownPath() throws SCMException
    {
        getServer("test-client");
        assertNull(server.getFileRevision("//depot2/this/path/is/wrong", new NumericalRevision(5)));
    }

    public void testGetFileRevisionBeforeFileAdded() throws SCMException
    {
        getServer("test-client");
        assertNull(server.getFileRevision("//depot2/file1", new NumericalRevision(1)));
    }

    public void testGetFileRevisionAfterFileDeleted() throws SCMException
    {
        getServer("test-client");
        FileRevision fileRevision = server.getFileRevision("//depot2/file10", new NumericalRevision(4));
        assertNull(fileRevision);
    }

    public void testGetRevision() throws SCMException
    {
        getServer("test-client");
        NumericalRevision rev = server.getRevision("3");
        assertEquals(3, rev.getRevisionNumber());
    }

    public void testGetRevisionLatest() throws SCMException
    {
        getServer("test-client");
        NumericalRevision latest = server.getLatestRevision();
        NumericalRevision rev = server.getRevision(latest.getRevisionString());
        assertEquals(latest.getRevisionNumber(), rev.getRevisionNumber());
    }

    public void testGetRevisionPostLatest() throws SCMException
    {
        getServer("test-client");
        NumericalRevision latest = server.getLatestRevision();
        try
        {
            server.getRevision(Long.toString(latest.getRevisionNumber() + 1));
            fail();
        }
        catch (SCMException e)
        {
            assertTrue(e.getMessage().matches(".*Change [0-9]+ unknown.*"));
        }
    }

    public void testGetRevisionInvalid() throws SCMException
    {
        getServer("test-client");
        try
        {
            server.getRevision("bullet");
            fail();
        }
        catch (SCMException e)
        {
            assertEquals("Invalid revision 'bullet': must be a valid Perforce changelist number", e.getMessage());
        }
    }

    private void getServer(String client)
    {
        server = new P4Server(":6666", "test-user", "", client);
        this.client.setEnv(P4Constants.ENV_PORT, ":6666");
        this.client.setEnv(P4Constants.ENV_USER, "test-user");
    }

    private void checkDirectory(String name) throws IOException
    {
        File expectedRoot = getDataRoot();
        File expectedDir = new File(expectedRoot, name);

        if (generateMode)
        {
            workDir.renameTo(expectedDir);
        }
        else
        {
            assertDirectoriesEqual(expectedDir, workDir);
        }
    }

    private File getDataRoot()
    {
        return new File(getPulseRoot(), FileSystemUtils.composeFilename("server-core", "src", "test", "com", "zutubi", "pulse", "scm", "p4", "data"));
    }

    private List<Change> checkoutChanges(String id, File dir, NumericalRevision revision, long expectedRevision) throws SCMException
    {
        SCMChangeAccumulator accumulator = new SCMChangeAccumulator();
        NumericalRevision rev = (NumericalRevision) server.checkout(id, dir, revision, accumulator);
        assertEquals(expectedRevision, rev.getRevisionNumber());
        return accumulator.getChanges();
    }

    private List<Change> updateChanges(String id, File dir, NumericalRevision revision) throws SCMException
    {
        SCMChangeAccumulator accumulator = new SCMChangeAccumulator();
        server.update(id, dir, revision, accumulator);
        return accumulator.getChanges();
    }
}
