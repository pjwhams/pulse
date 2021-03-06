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

package com.zutubi.pulse.core.commands.core;

import com.google.common.io.Files;
import com.zutubi.pulse.core.commands.api.TestCommandContext;
import com.zutubi.pulse.core.engine.api.BuildException;
import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.pulse.core.engine.api.ResourceProperty;
import com.zutubi.pulse.core.engine.api.ResultState;
import com.zutubi.util.SystemUtils;
import com.zutubi.util.io.FileSystemUtils;
import org.hamcrest.Matchers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import static com.zutubi.pulse.core.commands.api.OutputProducingCommandSupport.OUTPUT_FILE;
import static com.zutubi.pulse.core.commands.api.OutputProducingCommandSupport.OUTPUT_NAME;
import static com.zutubi.pulse.core.commands.core.ExecutableCommand.ENV_ARTIFACT_NAME;
import static com.zutubi.pulse.core.commands.core.ExecutableCommand.ENV_FILENAME;
import static com.zutubi.pulse.core.engine.api.BuildProperties.NAMESPACE_INTERNAL;
import static com.zutubi.pulse.core.engine.api.BuildProperties.PROPERTY_BUILD_NUMBER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class ExecutableCommandTest extends ExecutableCommandTestCase
{
    public void testExecuteSuccessExpected() throws Exception
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("echo");
        config.setArgs("hello world");

        successRun(new ExecutableCommand(config));
    }

    public void testExecuteFailureExpected() throws Exception
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe(SystemUtils.IS_WINDOWS ? "dir" : "ls");
        config.setArgs("wtfisgoingon");

        failedRun(new ExecutableCommand(config));
    }

    public void testExecuteSuccessExpectedNoArg() throws Exception
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("netstat");
        config.setArgs("-n");

        successRun(new ExecutableCommand(config));
    }

    public void testExecuteExceptionExpected() throws Exception
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("unknown");
        config.setArgs("command");

        ExecutableCommand command = new ExecutableCommand(config);
        TestCommandContext context = new TestCommandContext(createExecutionContext());
        try
        {
            command.execute(context);
            fail("Expect a build exception with a bad executable");
        }
        catch (BuildException e)
        {
            assertThat(e.getMessage(), Matchers.anyOf(containsString("No such executable"), containsString("unknown: not found")));
        }

        // verify that the env output is captured even with the command failing.
        assertArtifactRegistered(new TestCommandContext.Artifact(ENV_ARTIFACT_NAME), context);
        assertFileExists(ENV_ARTIFACT_NAME, ENV_FILENAME);
    }

    public void testExecuteExceptionWhenArgumentsAreIncludedInTheExecutable()
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("hammer.sh -j3 --mode=dbg  build/dbg/obj build/dbg/staging ");

        ExecutableCommand command = new ExecutableCommand(config);
        TestCommandContext context = new TestCommandContext(createExecutionContext());

        try
        {
            command.execute(context);
            fail("Expect a build exception with a bad executable");
        }
        catch (BuildException e)
        {
            assertThat(e.getMessage(), containsString("The command contains spaces"));
        }
    }

    public void testWorkingDir() throws Exception
    {
        File dir = new File(baseDir, "nested");
        File file;

        assertTrue(dir.mkdir());

        if (SystemUtils.IS_WINDOWS)
        {
            file = new File(dir, "list.bat");
            Files.write("dir", file, Charset.defaultCharset());
        }
        else
        {
            file = new File(dir, "./list.sh");
            Files.write("#! /bin/sh\nls", file, Charset.defaultCharset());
            FileSystemUtils.setPermissions(file, FileSystemUtils.PERMISSION_ALL_FULL);
        }

        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setWorkingDir("nested");
        config.setExe(file.getPath());

        successRun(new ExecutableCommand(config));
    }

    public void testRelativeExe() throws Exception
    {
        File dir = new File(baseDir, "nested");
        File file;
        String exe;

        assertTrue(dir.mkdir());

        if (SystemUtils.IS_WINDOWS)
        {
            exe = "list.bat";
            file = new File(dir, exe);
            Files.write("dir", file, Charset.defaultCharset());
        }
        else
        {
            exe = "list.sh";
            file = new File(dir, exe);
            Files.write("#! /bin/sh\nls", file, Charset.defaultCharset());
            FileSystemUtils.setPermissions(file, FileSystemUtils.PERMISSION_ALL_FULL);
        }

        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setWorkingDir("nested");
        config.setExe(exe);

        successRun(new ExecutableCommand(config));
    }

    public void testEnvironmentDetailsAreCaptured() throws Exception
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("echo");
        config.setArgs("hello world");

        ExecutableCommand command = new ExecutableCommand(config);
        TestCommandContext context = runCommand(command);

        assertEquals(2, context.getArtifacts().size());

        assertEnvironment(context, "Command Line:", "Process Environment:", "Resources:");

        assertArtifactRegistered(new TestCommandContext.Artifact(OUTPUT_NAME), context);
        assertFileExists(OUTPUT_NAME, OUTPUT_FILE);
    }

    public void testBuildNumberAddedToEnvironment() throws IOException
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("echo");

        ExecutableCommand command = new ExecutableCommand(config);
        assertEnvironment(runCommand(command, 1234), "PULSE_BUILD_NUMBER=1234");
    }

    public void testBuildNumberNotAddedToEnvironmentWhenNotSpecified() throws Exception
    {
        // if we are running in pulse, then PULSE_BUILD_NUMBER will already
        // be added to the environment.
        boolean runningInPulse = System.getenv().containsKey("PULSE_BUILD_NUMBER");

        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("echo");

        runCommand(new ExecutableCommand(config));

        String output = getFileContent(ENV_ARTIFACT_NAME, ENV_FILENAME);
        if (runningInPulse)
        {
            // should only appear once.
            assertTrue(output.indexOf("PULSE_BUILD_NUMBER") == output.lastIndexOf("PULSE_BUILD_NUMBER"));
        }
        else
        {
            // does not appear.
            assertFalse(output.contains("PULSE_BUILD_NUMBER"));
        }
    }

    public void testResourcePathsAddedToEnvironment() throws IOException
    {
        ExecutionContext context = createExecutionContext();
        context.add(new ResourceProperty("java.bin.dir", "somedir", false, true));
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("echo");

        ExecutableCommand command = new ExecutableCommand(config);
        assertEnvironment(runCommand(command, context), "path=somedir" + File.pathSeparator);
    }

    public void testNoSuchExecutableOnWindows()
    {
        if(!SystemUtils.IS_WINDOWS)
        {
            return;
        }

        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("thisfiledoesnotexist");

        ExecutableCommand command = new ExecutableCommand(config);
        try
        {
            runCommand(command, 1234);
            fail("Command with bad executable should throw.");
        }
        catch (BuildException e)
        {
            String message = e.getMessage();
            boolean java15 = message.contains("No such executable 'thisfiledoesnotexist'");
            // In Java 1.6, the error reporting is better, so we are
            // happy to pass it on through.
            boolean java16 = message.endsWith("The system cannot find the file specified");
            assertTrue(java15 || java16);
        }
    }

    public void testNoSuchWorkDir()
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("dir");
        config.setWorkingDir("nosuchworkdir");

        ExecutableCommand command = new ExecutableCommand(config);
        try
        {
            runCommand(command, 1234);
            fail("Command with bad working directory should throw.");
        }
        catch (BuildException e)
        {
            assertEquals("Working directory 'nosuchworkdir' does not exist", e.getMessage());
        }
    }

    public void testSpecifiedWorkDirIsFile() throws IOException
    {
        File plainFile = new File(baseDir, "f");
        assertTrue(plainFile.createNewFile());

        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("dir");
        config.setWorkingDir("f");

        ExecutableCommand command = new ExecutableCommand(config);
        try
        {
            runCommand(command, 1234);
            fail("Command with working directory that is a plain file should throw.");
        }
        catch (BuildException e)
        {
            assertEquals("Working directory 'f' exists, but is not a directory", e.getMessage());
        }
    }

    public void testProcessId() throws IOException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException
    {
        if(SystemUtils.IS_WINDOWS)
        {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd");
            Process p = processBuilder.start();
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class clazz = loader.loadClass("java.lang.ProcessImpl");
            assertNotNull(clazz);
            Field handleField = clazz.getDeclaredField("handle");
            handleField.setAccessible(true);
            long handle = handleField.getLong(p);
            System.out.println("handle = " + handle);
        }
    }

    public void testWindowsEnvironmentNames() throws Exception
    {
        if (SystemUtils.IS_WINDOWS)
        {
            ExecutionContext context = createExecutionContext();
            context.add(new ResourceProperty("a<>", "b", true, false));
            ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
            config.setExe("dir");

            ExecutableCommand command = new ExecutableCommand(config);
            runCommand(command, context);
        }
    }

    public void testStatusMapping() throws Exception
    {
        TestCommandContext context = statusMappingHelper(1, 1, ResultState.SUCCESS);
        assertEquals(ResultState.SUCCESS, context.getResultState());
    }

    public void testStatusMappingNoMatch() throws Exception
    {
        TestCommandContext context = statusMappingHelper(2, 1, ResultState.SUCCESS);
        assertEquals(ResultState.FAILURE, context.getResultState());
    }

    public void testStatusMappingError() throws Exception
    {
        TestCommandContext context = statusMappingHelper(2, 2, ResultState.ERROR);
        assertEquals(ResultState.ERROR, context.getResultState());
    }

    public void testArgumentCreationEmptyString()
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setArgs("");
        assertEquals(0, config.getCombinedArguments().size());
    }

    public void testArgumentCreationWhitespaceString()
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setArgs("  ");
        assertEquals(0, config.getCombinedArguments().size());
    }

    public void testArgumentCreationTrimsWhitespace()
    {
        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setArgs("  a   b ");
        List<String> args = config.getCombinedArguments();
        assertEquals(2, args.size());
        assertEquals("a", args.get(0));
        assertEquals("b", args.get(1));
    }

    private TestCommandContext statusMappingHelper(int exitCode, int mappedCode, ResultState mappedStatus) throws Exception
    {
        File jarFile = copyInputToDirectory("exit", "jar", baseDir);

        ExecutableCommandConfiguration config = new ExecutableCommandConfiguration();
        config.setExe("java");
        config.setExtraArguments(Arrays.asList("-jar", jarFile.getAbsolutePath(), Integer.toString(exitCode)));
        StatusMappingConfiguration mapping = new StatusMappingConfiguration();
        config.getStatusMappings().add(mapping);
        mapping.setCode(mappedCode);
        mapping.setStatus(mappedStatus);

        return runCommand(new ExecutableCommand(config));
    }

    private TestCommandContext runCommand(ExecutableCommand command, long buildNumber)
    {
        ExecutionContext context = createExecutionContext();
        context.addString(NAMESPACE_INTERNAL, PROPERTY_BUILD_NUMBER, Long.toString(buildNumber));
        return super.runCommand(command, context);
    }

    private void assertEnvironment(TestCommandContext context, String... contents) throws IOException
    {
        assertArtifactRegistered(new TestCommandContext.Artifact(ENV_ARTIFACT_NAME), context);
        assertFileExists(ENV_ARTIFACT_NAME, ENV_FILENAME);
        assertFileContains(ENV_ARTIFACT_NAME, ENV_FILENAME, false, contents);
    }
}
