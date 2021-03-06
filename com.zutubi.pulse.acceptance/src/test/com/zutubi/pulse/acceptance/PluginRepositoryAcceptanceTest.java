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

package com.zutubi.pulse.acceptance;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.zutubi.pulse.core.plugins.repository.PluginInfo;
import com.zutubi.pulse.core.plugins.repository.PluginRepository;
import com.zutubi.pulse.core.plugins.repository.http.HttpPluginRepository;
import com.zutubi.util.RandomUtils;
import com.zutubi.util.io.FileSystemUtils;
import com.zutubi.util.junit.IOAssertions;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;

public class PluginRepositoryAcceptanceTest extends AcceptanceTestBase
{
    private static final String ID_CORE_COMMANDS = "com.zutubi.pulse.core.commands.core";
    private static final String ID_EMAIL_HOOK_TASK = "com.zutubi.pulse.master.hook.email";

    private HttpPluginRepository repository;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        repository = new HttpPluginRepository(baseUrl + "/pluginrepository/");
        rpcClient.loginAsAdmin();
    }

    @Override
    protected void tearDown() throws Exception
    {
        rpcClient.logout();
        super.tearDown();
    }

    public void testListAvailable() throws IOException
    {
        Collection<PluginInfo> infoList = repository.getAvailablePlugins(PluginRepository.Scope.MASTER);
        assertTrue(isPluginListed(infoList, ID_CORE_COMMANDS));
        assertTrue(isPluginListed(infoList, ID_EMAIL_HOOK_TASK));

        infoList = repository.getAvailablePlugins(PluginRepository.Scope.CORE);
        assertTrue(isPluginListed(infoList, ID_CORE_COMMANDS));
        assertFalse(isPluginListed(infoList, ID_EMAIL_HOOK_TASK));
    }

    private boolean isPluginListed(Collection<PluginInfo> infoList, final String id)
    {
        return Iterables.any(infoList, new Predicate<PluginInfo>()
        {
            public boolean apply(PluginInfo pluginInfo)
            {
                return pluginInfo.getId().equals(id);
            }
        });
    }

    public void testDownloadPlugin() throws Exception
    {
        File tmpDir = FileSystemUtils.createTempDir(getName(), ".tmp");
        try
        {
            String random = RandomUtils.insecureRandomString(10);
            String id = AcceptanceTestUtils.PLUGIN_ID_TEST + "." + random;
            File pluginJar = AcceptanceTestUtils.createTestPlugin(tmpDir, id, getName() + "-" + random);
            rpcClient.TestApi.installPlugin(pluginJar.getAbsolutePath());
            
            File downloadedJar = downloadPlugin(tmpDir, id, "2.0.0");
            IOAssertions.assertFilesEqual(pluginJar, downloadedJar);
        }
        finally
        {
            FileSystemUtils.rmdir(tmpDir);
        }
    }

    private File downloadPlugin(File tmpDir, String id, String version) throws IOException
    {
        File jar = new File(tmpDir, id + "-" + version + "-downloaded.jar");
        URI uri = repository.getPluginLocation(new PluginInfo(id, version, PluginRepository.Scope.CORE));
        final GetMethod getMethod = AcceptanceTestUtils.httpGet(uri.toString(), null);
        try
        {
            new ByteSource()
            {
                @Override
                public InputStream openStream() throws IOException
                {
                    return getMethod.getResponseBodyAsStream();
                }
            }.copyTo(Files.asByteSink(jar));
            return jar;
        }
        finally
        {
            getMethod.releaseConnection();
        }
    }
}
