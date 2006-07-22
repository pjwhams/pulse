package com.zutubi.pulse.services;

import com.zutubi.pulse.bootstrap.*;
import com.zutubi.pulse.test.PulseTestCase;
import com.zutubi.pulse.util.FileSystemUtils;
import com.mockobjects.dynamic.Mock;
import com.mockobjects.dynamic.C;

import java.io.File;
import java.io.IOException;

/**
 */
public class ServiceTokenManagerTest extends PulseTestCase
{
    private static final String TEST_TOKEN = "test token string";

    private File tempDir;
    private ServiceTokenManager tokenManager;

    private ConfigurationManager configManager;

    protected void setUp() throws Exception
    {
        super.setUp();
        tempDir = FileSystemUtils.createTempDirectory(ServiceTokenManager.class.getName(), "");
        tokenManager = new ServiceTokenManager();
        DefaultSystemPaths paths = new DefaultSystemPaths(tempDir);
        paths.getConfigRoot().mkdirs();

        Mock mockConfigurationManager = new Mock(ConfigurationManager.class);
        mockConfigurationManager.matchAndReturn("getSystemPaths", C.ANY_ARGS, paths);
        configManager = (ConfigurationManager) mockConfigurationManager.proxy();

        tokenManager.setConfigurationManager(configManager);
    }

    protected void tearDown() throws Exception
    {
        removeDirectory(tempDir);
        super.tearDown();
    }

    public void testGeneratesToken()
    {
        assertNull(tokenManager.getToken());
        tokenManager.init();
        assertNotNull(tokenManager.getToken());
    }

    public void testUsesExistingToken() throws IOException
    {
        assertNull(tokenManager.getToken());
        File tokenFile = tokenManager.getTokenFile();
        FileSystemUtils.createFile(tokenFile, TEST_TOKEN);
        tokenManager.init();
        assertEquals(TEST_TOKEN, tokenManager.getToken());
    }

    public void testAcceptsToken()
    {
        tokenManager.init();
        String token = tokenManager.getToken();
        tokenManager.validateToken(token);
    }

    public void testRejectsInvalidToken()
    {
        tokenManager.init();
        String token = tokenManager.getToken();
        try
        {
            tokenManager.validateToken(token + "invalid");
            fail();
        }
        catch (InvalidTokenException e)
        {
        }
    }

    public void testAcceptsFirstToken()
    {
        tokenManager.setGenerate(false);
        tokenManager.init();
        assertNull(tokenManager.getToken());
        tokenManager.validateToken(TEST_TOKEN);
        assertEquals(TEST_TOKEN, tokenManager.getToken());
    }

    public void testGeneratedTokenPersists()
    {
        tokenManager.init();
        String token = tokenManager.getToken();

        ServiceTokenManager another = new ServiceTokenManager();
        another.setConfigurationManager(configManager);
        assertNull(another.getToken());
        another.init();
        assertEquals(token, another.getToken());
    }

    public void testAcceptedTokenPersists()
    {
        tokenManager.setGenerate(false);
        tokenManager.init();
        tokenManager.validateToken(TEST_TOKEN);

        ServiceTokenManager another = new ServiceTokenManager();
        another.setConfigurationManager(configManager);
        assertNull(another.getToken());
        another.init();
        assertEquals(TEST_TOKEN, another.getToken());
    }

    public void testTokenRefreshes()
    {
        tokenManager.setGenerate(false);
        tokenManager.init();
        tokenManager.validateToken(TEST_TOKEN);

        try
        {
            tokenManager.validateToken(TEST_TOKEN + "foo");
            fail();
        }
        catch(InvalidTokenException e)
        {
        }

        tokenManager.getTokenFile().delete();
        tokenManager.validateToken(TEST_TOKEN + "foo");
        assertEquals(TEST_TOKEN + "foo", tokenManager.getToken());
    }
}
