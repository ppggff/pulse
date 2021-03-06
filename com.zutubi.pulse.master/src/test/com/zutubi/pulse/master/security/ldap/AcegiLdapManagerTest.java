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

package com.zutubi.pulse.master.security.ldap;

import com.google.common.io.Resources;
import com.zutubi.pulse.master.model.UserManager;
import com.zutubi.pulse.master.tove.config.admin.LDAPConfiguration;
import com.zutubi.pulse.master.tove.config.user.UserConfiguration;
import com.zutubi.pulse.master.tove.config.user.contacts.ContactConfiguration;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;

import static com.zutubi.pulse.master.security.ldap.AcegiLdapManager.EMAIL_CONTACT_NAME;
import static junit.framework.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;


@RunWith(FrameworkRunner.class)
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", port = 1055)})
@CreateDS(partitions = {
@CreatePartition(name = "zutubi", suffix = "dc=ldap-test,dc=zutubi,dc=com")
})
public class AcegiLdapManagerTest extends AbstractLdapTestUnit
{
    private LDAPConfiguration config;

    private AcegiLdapManager manager;

    @Before
    public void setUp() throws Exception
    {
        config = createBaseConfiguration();

        final URL ldifUrl = AcegiLdapManagerTest.class.getResource("AcegiLdapManagerTest.ldif");
        String ldif = Resources.asCharSource(ldifUrl, Charset.defaultCharset()).read();
        DSAnnotationProcessor.injectEntries(service, ldif);

        UserManager userManager = mock(UserManager.class);
        addPulseUser(userManager, "dostermeier", "Daniel Ostermeier");

        addLdapUser("dostermeier", "Daniel", "Ostermeier", "secret", "daniel@zutubi.com");
        addLdapUser("jsankey", "Jason", "Sankey", "secret", "jason@zutubi.com");

        manager = new AcegiLdapManager();
        manager.setUserManager(userManager);
    }

    private LDAPConfiguration createBaseConfiguration()
    {
        LDAPConfiguration config = new LDAPConfiguration();
        config.setLdapUrl("ldap://localhost:" + ldapServer.getPort() + "/");
        config.setBaseDn("dc=ldap-test,dc=zutubi,dc=com");
        config.setEnabled(true);
        config.setUserBaseDn("");
        config.setUserFilter("(uid=${login})");
        config.setManagerDn("uid=admin,ou=system");
        config.setManagerPassword("secret");
        return config;
    }

    private void addPulseUser(UserManager userManager, String login, String name)
    {
        UserConfiguration daniel = new UserConfiguration(login, name);
        doReturn(daniel).when(userManager).getUser(eq(login));
    }

    private void addLdapUser(String login, String firstname, String surname, String password, String email) throws Exception
    {
        DSAnnotationProcessor.injectEntries(service,
                "dn: uid=" + login + ",ou=Users,dc=ldap-test,dc=zutubi,dc=com\n" +
                        "objectclass: inetOrgPerson\n" +
                        "cn: " + firstname + " " + surname + "\n" +
                        "sn: " + surname + "\n" +
                        "uid: " + login + "\n" +
                        "userpassword: " + password + "\n" +
                        "homephone: 555-111-2223\n" +
                        "mail: " + email + "\n" +
                        "ou: Users\n" +
                        "\n"
        );
    }

    @Test
    // authenticate a user that exists in the local configuration.
    public void testAuthenticateLocalUser()
    {
        manager.init(config);
        UserConfiguration user = manager.authenticate("dostermeier", "secret", false);
        assertNotNull(user);
        assertTrue(user.isAuthenticatedViaLdap());
    }

    @Test
    // authenticate a user that does not exist in the local configuration
    public void testAuthenticateLdapUser()
    {
        manager.init(config);
        UserConfiguration user = manager.authenticate("jsankey", "secret", false);
        assertNotNull(user);
        assertTrue(user.isAuthenticatedViaLdap());
    }

    @Test
    // authenticate a user with incorrect credentials.
    public void testAuthenticateFailure()
    {
        manager.init(config);
        assertNull(manager.authenticate("dostermeier", "wrongsecret", false));
    }

    @Test
    // sometimes, doing things multiple times hurts, so just in case.
    public void testMultipleAuthentications()
    {
        manager.init(config);
        assertNotNull(manager.authenticate("dostermeier", "secret", false));
        assertNotNull(manager.authenticate("jsankey", "secret", false));
    }

    @Test
    public void testAddContactOnAuthentication()
    {
        config.setEmailAttribute("mail");
        manager.init(config);
        UserConfiguration user = manager.authenticate("dostermeier", "secret", true);
        
        Map<String, ContactConfiguration> contacts = user.getPreferences().getContacts();
        ContactConfiguration contact = contacts.get(EMAIL_CONTACT_NAME);
        assertNotNull(contact);
        assertEquals("daniel@zutubi.com", contact.getUniqueId());
    }

    @Test
    public void testConfigurationWithInvalidUrl()
    {
        LDAPConfiguration config = createBaseConfiguration();
        config.setLdapUrl("ldap://localhost:666/");
        checkConfigurationFails(config, "dostermeier", "secret");
    }

    @Test
    public void testConfigurationWithBadCredentials()
    {
        LDAPConfiguration config = createBaseConfiguration();
        checkConfigurationFails(config, "dostermeier", "wrong secret");
    }

    @Test
    public void testStatusMessageFromInitFailure()
    {
        config.setUserBaseDn(null);
        manager.init(config);
        assertEquals("searchBase must not be null (an empty string is acceptable).", manager.getStatusMessage());
    }

    private void checkConfigurationFails(LDAPConfiguration config, String login, String password)
    {
        try
        {
            manager.testAuthenticate(config, login, password);
            fail();
        }
        catch (Exception e)
        {
            // expected.
        }
    }
}