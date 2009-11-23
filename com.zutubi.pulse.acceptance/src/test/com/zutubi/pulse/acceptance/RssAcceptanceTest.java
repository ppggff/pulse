package com.zutubi.pulse.acceptance;

import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import com.zutubi.i18n.Messages;
import com.zutubi.pulse.master.xwork.actions.rss.BuildResultsRssAction;
import com.zutubi.util.RandomUtils;
import com.zutubi.util.io.IOUtils;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RssAcceptanceTest extends BaseXmlRpcAcceptanceTest
{
    private static final Messages I18N = Messages.getInstance(BuildResultsRssAction.class);

    protected void setUp() throws Exception
    {
        super.setUp();

        xmlRpcHelper.loginAsAdmin();
    }

    public void testProjectRssFeedGeneration() throws Exception
    {
        String projectName = randomName();

        createProject(projectName, 1);

        SyndFeed feed = readFeed("rss.action?projectName=" + projectName);
        assertThat(feed.getDescription(), containsString(projectName));
        assertEquals(1, feed.getEntries().size());
    }

    public void testUnknownProjectName() throws FeedException, IOException
    {
        String projectName = randomName();

        SyndFeed feed = readFeed("rss.action?projectName=" + projectName);
        assertThat(feed.getTitle(), equalTo(I18N.format("unknown.project.title", new Object[]{projectName})));
        assertThat(feed.getDescription(), equalTo(I18N.format("unknown.project.description", new Object[]{projectName})));
        assertEquals(0, feed.getEntries().size());
    }

    public void testUnknownProjectId() throws FeedException, IOException
    {
        long projectId = RandomUtils.randomLong();

        SyndFeed feed = readFeed("rss.action?projectId=" + projectId);
        assertThat(feed.getTitle(), equalTo(I18N.format("unknown.project.title", projectId)));
        assertThat(feed.getDescription(), equalTo(I18N.format("unknown.project.description", projectId)));
        assertEquals(0, feed.getEntries().size());
    }

    public void testUnknownUser() throws FeedException, IOException
    {
        long userId = RandomUtils.randomLong();

        SyndFeed feed = readFeed("rss.action?userId=" + userId);
        assertThat(feed.getTitle(), equalTo(I18N.format("unknown.user.title", userId)));
        assertThat(feed.getDescription(), equalTo(I18N.format("unknown.user.description", userId)));
        assertEquals(0, feed.getEntries().size());
    }

    public void testUnknownProjectGroup() throws FeedException, IOException
    {
        String groupName = randomName();

        SyndFeed feed = readFeed("rss.action?groupName=" + groupName);
        assertThat(feed.getTitle(), equalTo(I18N.format("unknown.group.title", new Object[]{groupName})));
        assertThat(feed.getDescription(), equalTo(I18N.format("unknown.group.description", new Object[]{groupName})));
        assertEquals(0, feed.getEntries().size());
    }

    public void testAllBuildsRssFeedGeneration() throws Exception
    {
        SyndFeed feed = readFeed("rss.action");
        assertThat(feed.getTitle(), equalTo("Pulse build results"));
        assertThat(feed.getDescription(), equalTo("This feed contains the latest pulse build results."));
    }

    // test authenticated access to the feeds.

    // test content of the feeds - expected builds are returned.

    private void createProject(String projectName, int buildCount) throws Exception
    {
        xmlRpcHelper.insertSimpleProject(projectName, false);
        xmlRpcHelper.waitForProjectToInitialise(projectName);
        for (int i = 1; i <= buildCount; i++)
        {
            xmlRpcHelper.triggerBuild(projectName);
            xmlRpcHelper.waitForBuildToComplete(projectName, i, 5000);
        }
    }

    private SyndFeed readFeed(String path) throws FeedException, IOException
    {
        String content = readUriContent(baseUrl + path);

        return new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(content.getBytes())));
    }

    private String readUriContent(String contentUri) throws IOException
    {
        UsernamePasswordCredentials adminCredentials = new UsernamePasswordCredentials("admin", "admin");
        return readUriContent(contentUri, adminCredentials);
    }

    private String readUriContent(String contentUri, Credentials credentials) throws IOException
    {
        HttpClient client = new HttpClient();

        client.getState().setCredentials(AuthScope.ANY, credentials);
        client.getParams().setAuthenticationPreemptive(true); // our Basic authentication does not challenge.

        GetMethod get = new GetMethod(contentUri);

        InputStream input = null;
        try
        {
            assertEquals(HttpStatus.SC_OK, client.executeMethod(get));
            input = get.getResponseBodyAsStream();
            return IOUtils.inputStreamToString(input);
        }
        finally
        {
            IOUtils.close(input);
            get.releaseConnection();
        }
    }
}