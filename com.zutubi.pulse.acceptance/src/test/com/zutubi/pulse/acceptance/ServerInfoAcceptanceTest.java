package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.components.table.KeyValueTable;
import com.zutubi.pulse.acceptance.pages.agents.AgentInfoPage;
import com.zutubi.pulse.acceptance.pages.server.ServerInfoPage;
import com.zutubi.pulse.master.agent.AgentManager;
import com.zutubi.util.SystemUtils;

import java.util.Map;

/**
 * Acceptance tests for the server > info tabs and agent info tabs.
 */
public class ServerInfoAcceptanceTest extends AcceptanceTestBase
{
    public void testServerInfoAsAdmin() throws Exception
    {
        getBrowser().loginAsAdmin();
        ServerInfoPage infoPage = getBrowser().openAndWaitFor(ServerInfoPage.class);
        checkPulseProperties(infoPage);
        checkServerProperties(infoPage);
        checkJvmProperties(infoPage);
        checkEnvironment(infoPage);
    }

    public void testServerInfoAsNonAdmin() throws Exception
    {
        rpcClient.loginAsAdmin();
        rpcClient.RemoteApi.insertTrivialUser(random);
        rpcClient.logout();
        
        getBrowser().login(random, "");
        ServerInfoPage infoPage = getBrowser().openAndWaitFor(ServerInfoPage.class);
        checkPulseProperties(infoPage);
        checkServerProperties(infoPage);

        KeyValueTable jvmProperties = infoPage.getJvmProperties();
        assertFalse(jvmProperties.isPresent());

        KeyValueTable environment = infoPage.getEnvironment();
        assertFalse(environment.isPresent());
    }

    public void testAgentInfo() throws Exception
    {
        getBrowser().loginAsAdmin();
        ServerInfoPage infoPage = getBrowser().openAndWaitFor(AgentInfoPage.class, AgentManager.MASTER_AGENT_NAME);
        checkPulseProperties(infoPage);
        checkServerProperties(infoPage);
        checkJvmProperties(infoPage);
        checkEnvironment(infoPage);
    }

    public void testAgentInfoAgentOffline() throws Exception
    {
        rpcClient.loginAsAdmin();
        String agentPath = rpcClient.RemoteApi.insertSimpleAgent(random, "localhost", 555666777);
        try
        {
            getBrowser().loginAsAdmin();
            AgentInfoPage page = getBrowser().createPage(AgentInfoPage.class, random);
            page.open();
            getBrowser().waitForPageToLoad();
            assertTrue(getBrowser().isTextPresent("Agent is not online"));
        }
        finally
        {
            rpcClient.RemoteApi.deleteConfig(agentPath);
            rpcClient.logout();
        }
    }

    public void testAgentInfoAsNonAdmin() throws Exception
    {
        rpcClient.loginAsAdmin();
        rpcClient.RemoteApi.insertTrivialUser(random);
        rpcClient.logout();

        getBrowser().login(random, "");
        AgentInfoPage infoPage = getBrowser().openAndWaitFor(AgentInfoPage.class, AgentManager.MASTER_AGENT_NAME);
        checkPulseProperties(infoPage);
        checkServerProperties(infoPage);

        KeyValueTable jvmProperties = infoPage.getJvmProperties();
        assertFalse(jvmProperties.isPresent());

        KeyValueTable environment = infoPage.getEnvironment();
        assertFalse(environment.isPresent());
    }

    private void checkServerProperties(ServerInfoPage infoPage)
    {
        Map<String, String> pairs;KeyValueTable serverProperties = infoPage.getServerProperties();
        assertTrue(serverProperties.isPresent());
        pairs = serverProperties.getKeyValuePairs();
        assertEquals(11, pairs.size());
        assertTrue(pairs.containsKey("system uptime"));
    }

    private void checkPulseProperties(ServerInfoPage infoPage)
    {
        KeyValueTable pulseProperties = infoPage.getPulseProperties();
        assertTrue(pulseProperties.isPresent());
        Map<String,String> pairs = pulseProperties.getKeyValuePairs();
        int expectedSize = 10;
        if (pairs.containsKey("pulse home directory"))
        {
            expectedSize++;
        }

        assertEquals(expectedSize, pairs.size());
        assertTrue(pairs.containsKey("context path"));
    }

    private void checkJvmProperties(ServerInfoPage infoPage)
    {
        KeyValueTable jvmProperties = infoPage.getJvmProperties();
        assertTrue(jvmProperties.isPresent());
        Map<String, String> pairs = jvmProperties.getKeyValuePairs();
        assertTrue(pairs.containsKey("os.name"));
    }

    private void checkEnvironment(ServerInfoPage infoPage)
    {
        KeyValueTable environment = infoPage.getEnvironment();
        assertTrue(environment.isPresent());
        if (SystemUtils.IS_LINUX)
        {
            Map<String, String> pairs = environment.getKeyValuePairs();
            assertTrue(pairs.containsKey("PATH"));
        }
    }
    
}
