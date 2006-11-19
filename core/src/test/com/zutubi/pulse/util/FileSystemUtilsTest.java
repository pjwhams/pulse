package com.zutubi.pulse.util;

import com.zutubi.pulse.test.PulseTestCase;

import java.io.*;
import java.util.Arrays;
import java.util.zip.ZipInputStream;

/**
 */
public class FileSystemUtilsTest extends PulseTestCase
{
    private File tmpDir;

    protected void setUp() throws Exception
    {
        super.setUp();

        tmpDir = FileSystemUtils.createTempDir("FileSystemUtilsTest", getName());
    }

    protected void tearDown() throws Exception
    {
        removeDirectory(tmpDir);

        super.tearDown();
    }

    public void testGetPermissions()
    {
        if (!SystemUtils.IS_WINDOWS)
        {
            int permissions = FileSystemUtils.getPermissions(new File("/bin/sh"));
            // It is a link, hence the full permissions
            assertEquals(FileSystemUtils.PERMISSION_ALL_FULL, permissions);
        }
    }

    public void testSetPermissions() throws IOException
    {
        if (!SystemUtils.IS_WINDOWS)
        {
            File tmpDir = FileSystemUtils.createTempDir(getClass().getName(), "");
            FileSystemUtils.setPermissions(tmpDir, 0);
            int permissions = FileSystemUtils.getPermissions(tmpDir);
            assertEquals(permissions, 0);
            assertTrue(FileSystemUtils.rmdir(tmpDir));
        }
    }

    public void testSetExecutableOn() throws IOException
    {
        if(!SystemUtils.IS_WINDOWS)
        {
            File temp = File.createTempFile(FileSystemUtils.class.getName(), ".tmp");
            try
            {
                FileSystemUtils.setPermissions(temp, FileSystemUtils.PERMISSION_ALL_READ);
                assertEquals(FileSystemUtils.PERMISSION_ALL_READ, FileSystemUtils.getPermissions(temp));
                FileSystemUtils.setExecutable(temp, true);
                assertEquals(FileSystemUtils.PERMISSION_ALL_READ | FileSystemUtils.PERMISSION_ALL_EXECUTE, FileSystemUtils.getPermissions(temp));
            }
            finally
            {
                temp.delete();
            }
        }
    }

    public void testSetExecutableOff() throws IOException
    {
        if(!SystemUtils.IS_WINDOWS)
        {
            File temp = File.createTempFile(FileSystemUtils.class.getName(), ".tmp");
            try
            {
                FileSystemUtils.setPermissions(temp, FileSystemUtils.PERMISSION_ALL_FULL);
                assertEquals(FileSystemUtils.PERMISSION_ALL_FULL, FileSystemUtils.getPermissions(temp));
                FileSystemUtils.setExecutable(temp, false);
                assertEquals(FileSystemUtils.PERMISSION_ALL_READ | FileSystemUtils.PERMISSION_ALL_WRITE, FileSystemUtils.getPermissions(temp));
            }
            finally
            {
                temp.delete();
            }
        }
    }

    //@Required(tmpDir)
    public void testExtractEmptyFile() throws Exception
    {
        extractTestZipToTmp();

        // ensure that the expected directories exist.
        assertTrue(new File(tmpDir, "dir").isDirectory());
        assertTrue(new File(tmpDir, FileSystemUtils.composeFilename("dir", "subdirectory")).isDirectory());
        assertTrue(new File(tmpDir, FileSystemUtils.composeFilename("dir", "subdirectory", "emptyfile")).isFile());
    }

    public void testExtractNonEmptyFiles() throws Exception
    {
        extractTestZipToTmp();

        // ensure that the expected directories exist.
        assertTrue(new File(tmpDir, FileSystemUtils.composeFilename("config")).isDirectory());
        assertTrue(new File(tmpDir, FileSystemUtils.composeFilename("pulse.config.properties")).isFile());
        assertTrue(new File(tmpDir, FileSystemUtils.composeFilename("config", "pulse.properties")).isFile());
    }

    public void testCopyFileToFile() throws Exception
    {
        File from = new File(tmpDir, "from");
        File to = new File(tmpDir, "to");

        FileSystemUtils.createFile(from, "test");
        FileSystemUtils.copy(to, from);
        assertFilesEqual(from, to);
    }

    public void testFilesMatchBothEmpty() throws IOException
    {
        filesMatchHelper("", "", true);
    }

    public void testFilesMatchFirstShorter() throws IOException
    {
        filesMatchHelper("short", "longer", false);
    }

    public void testFilesMatchSecondShorter() throws IOException
    {
        filesMatchHelper("longer", "short", false);
    }

    public void testFilesMatchSameContent() throws IOException
    {
        filesMatchHelper("content", "content", true);
    }

    public void testFilesMatchSameLength() throws IOException
    {
        filesMatchHelper("content", "CONTENT", false);
    }

    public void testFilesMatchLongFiles() throws IOException
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10000; i++)
        {
            builder.append("some random string");
        }

        String content = builder.toString();
        filesMatchHelper(content, content, true);
    }

    public void testOverwritePreservesPermissions() throws IOException
    {
        if (SystemUtils.IS_LINUX)
        {
            File f = null;
            try
            {
                f = File.createTempFile(FileSystemUtils.class.getName(), ".txt");
                assertTrue(FileSystemUtils.PERMISSION_ALL_FULL != FileSystemUtils.getPermissions(f));
                FileSystemUtils.setPermissions(f, FileSystemUtils.PERMISSION_ALL_FULL);
                assertEquals(FileSystemUtils.PERMISSION_ALL_FULL, FileSystemUtils.getPermissions(f));

                FileOutputStream os = null;
                try
                {
                    os = new FileOutputStream(f);
                    os.write("hello".getBytes());
                }
                finally
                {
                    IOUtils.close(os);
                }

                assertEquals(FileSystemUtils.PERMISSION_ALL_FULL, FileSystemUtils.getPermissions(f));
            }
            finally
            {
                if (f != null)
                {
                    f.delete();
                }
            }
        }
    }

    public void testRenameOverNukesPermissions() throws IOException
    {
        if (SystemUtils.IS_LINUX)
        {
            File f = null;
            try
            {
                f = File.createTempFile(FileSystemUtils.class.getName(), ".txt");
                assertTrue(FileSystemUtils.PERMISSION_ALL_FULL != FileSystemUtils.getPermissions(f));
                FileSystemUtils.setPermissions(f, FileSystemUtils.PERMISSION_ALL_FULL);
                assertEquals(FileSystemUtils.PERMISSION_ALL_FULL, FileSystemUtils.getPermissions(f));

                File temp = File.createTempFile(FileSystemUtilsTest.class.getName(), "");
                temp.renameTo(f);
                assertTrue(FileSystemUtils.PERMISSION_ALL_FULL != FileSystemUtils.getPermissions(f));
            }
            finally
            {
                if (f != null)
                {
                    f.delete();
                }
            }
        }
    }

    public void testGetAndSetPermissionPerformance() throws IOException
    {
        if (!SystemUtils.IS_WINDOWS)
        {
            File f = null;
            try
            {
                f = File.createTempFile(FileSystemUtils.class.getName(), ".txt");

                long startTime = System.currentTimeMillis();
                for (int i = 0; i < 100; i++)
                {
                   FileSystemUtils.getPermissions(f);
                }
                
                long runningTime = System.currentTimeMillis() - startTime;
                System.out.printf("Can get permissions %.2f times/second\n", 100000.0 / runningTime);

                startTime = System.currentTimeMillis();
                for (int i = 0; i < 100; i++)
                {
                   FileSystemUtils.setPermissions(f, FileSystemUtils.PERMISSION_ALL_EXECUTE);
                }

                runningTime = System.currentTimeMillis() - startTime;
                System.out.printf("Can set permissions %.2f times/second\n", 100000.0 / runningTime);

                startTime = System.currentTimeMillis();
                for (int i = 0; i < 100; i++)
                {
                   FileSystemUtils.getPermissions(f);
                   FileSystemUtils.setPermissions(f, FileSystemUtils.PERMISSION_ALL_EXECUTE);
                }

                runningTime = System.currentTimeMillis() - startTime;
                System.out.printf("Can get and set permissions %.2f times/second\n", 100000.0 / runningTime);
            }
            finally
            {
                if (f != null)
                {
                    f.delete();
                }
            }
        }
    }

    public void testTranslateEOLLF() throws IOException
    {
        simpleEOLTest(SystemUtils.LF_BYTES, "line 1\nline 2\nline 3\nline 4\nline 5\nline 6\nline 7\nline 8\n");
    }

    public void testTranslateEOLCR() throws IOException
    {
        simpleEOLTest(SystemUtils.CR_BYTES, "line 1\rline 2\rline 3\rline 4\rline 5\rline 6\rline 7\rline 8\r");
    }

    public void testTranslateEOLCRLF() throws IOException
    {
        simpleEOLTest(SystemUtils.CRLF_BYTES, "line 1\r\nline 2\r\nline 3\r\nline 4\r\nline 5\r\nline 6\r\nline 7\r\nline 8\r\n");
    }

    public void testTranslateEOLPreservePermissions() throws IOException
    {
        if (SystemUtils.IS_LINUX)
        {
            File test = null;
            try
            {
                test = FileSystemUtils.createTempFile(FileSystemUtilsTest.class.getName(), ".tmp", "line 1\nline 2\nline 3\r\nline 4\nline 5\rline 6\r\nline 7\rline 8\r");
                FileSystemUtils.setPermissions(test, FileSystemUtils.PERMISSION_ALL_FULL);
                FileSystemUtils.translateEOLs(test, SystemUtils.CRLF_BYTES, true);
                assertEquals("line 1\r\nline 2\r\nline 3\r\nline 4\r\nline 5\r\nline 6\r\nline 7\r\nline 8\r\n", IOUtils.fileToString(test));
                assertEquals(FileSystemUtils.PERMISSION_ALL_FULL, FileSystemUtils.getPermissions(test));
            }
            finally
            {
                if(test != null)
                {
                    test.delete();
                }
            }
        }
    }

    public void testTranslateEOLEmptyFile() throws IOException
    {
        File test = null;
        try
        {
            test = FileSystemUtils.createTempFile(FileSystemUtilsTest.class.getName(), ".tmp", "");
            FileSystemUtils.translateEOLs(test, SystemUtils.CRLF_BYTES, true);
            assertEquals("", IOUtils.fileToString(test));
        }
        finally
        {
            if(test != null)
            {
                test.delete();
            }
        }
    }

    public void testTranslateEOLJustLF() throws IOException
    {
        File test = null;
        try
        {
            test = FileSystemUtils.createTempFile(FileSystemUtilsTest.class.getName(), ".tmp", "\n");
            FileSystemUtils.translateEOLs(test, SystemUtils.CRLF_BYTES, true);
            assertEquals("\r\n", IOUtils.fileToString(test));
        }
        finally
        {
            if(test != null)
            {
                test.delete();
            }
        }
    }

    public void testTranslateEOLTwoCRs() throws IOException
    {
        File test = null;
        try
        {
            test = FileSystemUtils.createTempFile(FileSystemUtilsTest.class.getName(), ".tmp", "\r\r");
            FileSystemUtils.translateEOLs(test, SystemUtils.LF_BYTES, true);
            assertEquals("\n\n", IOUtils.fileToString(test));
        }
        finally
        {
            if(test != null)
            {
                test.delete();
            }
        }
    }

    public void testTranslateEOLCRLFAcrossBoundary() throws IOException
    {
        byte [] in = new byte[1030];
        byte [] out = new byte[1029];

        Arrays.fill(in, (byte) 'x');
        Arrays.fill(out, (byte) 'x');

        in[1023] = '\r';
        in[1024] = '\n';
        out[1023] = '\n';

        File test = null;
        try
        {
            test = FileSystemUtils.createTempFile(FileSystemUtilsTest.class.getName(), ".tmp", in);
            FileSystemUtils.translateEOLs(test, SystemUtils.LF_BYTES, true);

            byte[] got = IOUtils.fileToBytes(test);
            assertEquals(out.length, got.length);
            for(int i = 0; i < out.length; i++)
            {
                assertEquals(out[i], got[i]);
            }
        }
        finally
        {
            if(test != null)
            {
                test.delete();
            }
        }
    }

    public void testCopyDirectoryToDirectory() throws Exception
    {
        File fromDir = new File(tmpDir, "from");
        File toDir = new File(tmpDir, "to");
        File f1 = new File(fromDir, "f1");
        File dir = new File(fromDir, "dir");
        File f2 = new File(dir, "f2");
        File f3 = new File(dir, "f3");
        File nested = new File(dir, "nested");
        File f4 = new File(nested, "f4");

        assertTrue(nested.mkdirs());
        FileSystemUtils.createFile(f1, "test f1");
        FileSystemUtils.createFile(f2, "test f2");
        FileSystemUtils.createFile(f3, "test f3");
        FileSystemUtils.createFile(f4, "test f4");

        FileSystemUtils.copy(toDir, fromDir);
        assertDirectoriesEqual(fromDir, toDir);
    }

    public void testCopyDirectoryToExistingDirectory() throws Exception
    {
        File fromDir = new File(tmpDir, "from");
        File toDir = new File(tmpDir, "to");
        File f1 = new File(fromDir, "f1");
        File dir = new File(fromDir, "dir");
        File f2 = new File(dir, "f2");
        File f3 = new File(dir, "f3");
        File nested = new File(dir, "nested");
        File f4 = new File(nested, "f4");

        assertTrue(toDir.mkdirs());
        assertTrue(nested.mkdirs());
        FileSystemUtils.createFile(f1, "test f1");
        FileSystemUtils.createFile(f2, "test f2");
        FileSystemUtils.createFile(f3, "test f3");
        FileSystemUtils.createFile(f4, "test f4");

        FileSystemUtils.copy(toDir, fromDir);
        assertDirectoriesEqual(fromDir, toDir);
    }

    public void testRecursiveCopyPreservesPermissions() throws Exception
    {
        if (SystemUtils.IS_LINUX)
        {
            File from = new File(tmpDir, "from");
            File to = new File(tmpDir, "to");

            FileSystemUtils.createFile(from, "test");
            FileSystemUtils.setPermissions(from, 777);
            FileSystemUtils.copy(to, from);
            assertEquals(777, FileSystemUtils.getPermissions(to));
        }
    }

    public void testCopyFileToNonExistantFile() throws IOException
    {
        File src = new File(tmpDir, "src.txt");
        FileSystemUtils.createFile(src, "Some text.");

        File dest = new File(tmpDir, "dest.txt");

        assertFalse(dest.isFile());
        FileSystemUtils.copy(dest, src);
        assertTrue(dest.isFile());
    }

    public void testCopyFileToDirectory() throws IOException
    {
        File src = new File(tmpDir, "src.txt");
        FileSystemUtils.createFile(src, "Some text.");

        File dest = new File(tmpDir, "dest");
        assertTrue(dest.mkdirs());

        assertTrue(dest.isDirectory());
        FileSystemUtils.copy(dest, src);
        assertTrue(new File(dest, "src.txt").isFile());
    }

    public void testCopyMultipleFiles() throws IOException
    {
        File srcA = new File(tmpDir, "srca.txt");
        FileSystemUtils.createFile(srcA, "Some text.");
        File srcB = new File(tmpDir, "srcb.txt");
        FileSystemUtils.createFile(srcB, "Some text.");

        File dest = new File(tmpDir, "dest");

        assertFalse(dest.isDirectory());
        FileSystemUtils.copy(dest, srcA, srcB);

        assertTrue(dest.isDirectory());
        assertTrue(new File(dest, "srca.txt").isFile());
        assertTrue(new File(dest, "srcb.txt").isFile());
    }

    public void testCopyDirectoryToNonExistantFile() throws IOException
    {
        File dir = new File(tmpDir, "dir");
        dir.mkdirs();

        FileSystemUtils.createFile(new File(dir, "a.txt"), "Text file a");
        FileSystemUtils.createFile(new File(dir, "b.txt"), "Text file a");

        File dest = new File(tmpDir, "dest");
        assertFalse(dest.exists());
        FileSystemUtils.copy(dest, dir);
        assertTrue(dest.isDirectory());
        assertTrue(new File(dest, "a.txt").isFile());
        assertTrue(new File(dest, "b.txt").isFile());
    }

    private void simpleEOLTest(byte[] eol, String out) throws IOException
    {
        File test = null;
        try
        {
            test = FileSystemUtils.createTempFile(FileSystemUtilsTest.class.getName(), ".tmp", "line 1\nline 2\nline 3\r\nline 4\nline 5\rline 6\r\nline 7\rline 8\r");
            FileSystemUtils.translateEOLs(test, eol, false);
            assertEquals(out, IOUtils.fileToString(test));
        }
        finally
        {
            if(test != null)
            {
                test.delete();
            }
        }
    }

    private void filesMatchHelper(String s1, String s2, boolean expected) throws IOException
    {
        File f1 = new File(tmpDir, "f1");
        File f2 = new File(tmpDir, "f2");

        FileSystemUtils.createFile(f1, s1);
        FileSystemUtils.createFile(f2, s2);

        assertEquals(expected, FileSystemUtils.filesMatch(f1, f2));
    }

    private void extractTestZipToTmp() throws IOException
    {
        InputStream is = null;
        try
        {
            File dataFile = getTestDataFile("core", getName(), "zip");
            is = new FileInputStream(dataFile);
            assertNotNull(is);
            FileSystemUtils.extractZip(new ZipInputStream(is), tmpDir);
        }
        finally
        {
            IOUtils.close(is);
        }
    }
}
