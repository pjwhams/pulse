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

package com.zutubi.pulse.master.bootstrap;

import com.zutubi.pulse.Version;
import com.zutubi.pulse.core.test.api.PulseTestCase;
import com.zutubi.pulse.servercore.bootstrap.DefaultSystemPaths;
import com.zutubi.util.io.FileSystemUtils;

import java.io.File;
import java.io.IOException;

public class DataTest extends PulseTestCase
{
    private File dataDir;
    private DefaultSystemPaths systemPaths;
    private File tempDir;

    protected void setUp() throws Exception
    {
        super.setUp();

        tempDir = FileSystemUtils.createTempDir(DataTest.class.getName(), "");
        dataDir = new File(tempDir, "data");
        dataDir.delete();

        systemPaths = new DefaultSystemPaths(new File(tempDir, "pulse-home"), new File(tempDir, "version-home"));
        File configRoot = systemPaths.getConfigRoot();
        configRoot.mkdirs();
        File dbtemplate = new File(configRoot, "database.user.properties.template");
        dbtemplate.createNewFile();
    }

    protected void tearDown() throws Exception
    {
        removeDirectory(tempDir);

        super.tearDown();
    }

    public void testInitialiseData() throws IOException
    {
        Data data = new Data(dataDir);

        // ensure that this works when the data directory does not exist.
        assertFalse(data.isInitialised());

        // ensure that this works when the data directory exists.
        assertTrue(dataDir.mkdirs());
        assertFalse(data.isInitialised());

        data.init(systemPaths);

        assertTrue(data.isInitialised());
        assertTrue(dataDir.exists());
        assertTrue(new File(dataDir, Data.CONFIG_FILE_NAME).exists());
    }

    public void testVersionDetails() throws IOException
    {
        Data data = new Data(dataDir);
        data.init(systemPaths);

        Version v = Version.getVersion();
        Version dataVersion = data.getVersion();
        assertEquals(v.getBuildNumber(), dataVersion.getBuildNumber());
        assertEquals(v.getVersionNumber(), dataVersion.getVersionNumber());
    }
}
