package com.zutubi.pulse.model;

import com.mockobjects.dynamic.C;
import com.mockobjects.dynamic.Mock;
import com.zutubi.config.annotations.Transient;
import com.zutubi.pulse.core.model.Revision;
import com.zutubi.pulse.core.scm.DelegateScmClientFactory;
import com.zutubi.pulse.core.scm.ScmClient;
import com.zutubi.pulse.core.scm.ScmException;
import com.zutubi.pulse.core.scm.config.ScmConfiguration;
import com.zutubi.pulse.prototype.config.project.BuildOptionsConfiguration;
import com.zutubi.pulse.prototype.config.project.ProjectConfiguration;
import com.zutubi.pulse.test.PulseTestCase;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 */
public class ChangelistIsolatorTest extends PulseTestCase
{
    private ChangelistIsolator isolator;
    private Mock mockBuildManager;
    private Mock mockScm;
    private ScmClient scmClient;
    private Project project;
    private ProjectConfiguration projectConfig;

    protected void setUp() throws Exception
    {
        super.setUp();

        mockBuildManager = new Mock(BuildManager.class);
        mockScm = new Mock(ScmClient.class);

        project = new Project();
        projectConfig = new ProjectConfiguration();
        projectConfig.setScm(new ScmConfiguration()
        {
            @Transient
            public String getType()
            {
                return "mock";
            }

            public String getPreviousRevision(String revision)
            {
                return null;
            }
        });
        BuildOptionsConfiguration options = new BuildOptionsConfiguration();
        options.setIsolateChangelists(true);
        projectConfig.setOptions(options);
    }

    public void testNeverBuilt() throws ScmException
    {
        returnNoMoreBuilds();
        returnLatestBuild(10);
        setupIsolator();
        expectRevisions(true, 10);
    }

    public void testPreviouslyBuilt() throws ScmException
    {
        returnBuild(55);
        returnRevisions(55, 56, 57);
        setupIsolator();
        expectRevisions(true, 56, 57);
    }

    public void testNoNewRevisions() throws ScmException
    {
        returnBuild(55);
        returnRevisions(55);
        setupIsolator();
        expectRevisions(false);
    }

    public void testNoNewRevisionsForced() throws ScmException
    {
        returnBuild(55);
        returnRevisions(55);
        setupIsolator();
        expectRevisions(true, 55);
    }

    public void testRemembersPreviousRevision() throws ScmException
    {
        returnBuild(101);
        returnRevisions(101, 102, 103, 104);
        returnRevisions(104, 105);
        setupIsolator();
        expectRevisions(true, 102, 103, 104);
        expectRevisions(true, 105);
    }

    public void testNullRevision() throws ScmException
    {
        returnNullDetails();
        returnNoMoreBuilds();
        returnLatestBuild(22);
        setupIsolator();
        expectRevisions(true, 22);
    }

    public void testSearchesBeyondNullRevision() throws ScmException
    {
        returnNullDetails();
        returnNullDetails();
        returnBuild(9);
        returnNoMoreBuilds();
        returnRevisions(9, 10, 11, 13);
        setupIsolator();
        expectRevisions(true, 10, 11, 13);
    }

    public void testReturnsDifferentRevisionObject() throws ScmException
    {
        Revision rev = returnBuild(10);
        returnRevisions(10);
        setupIsolator();

        List<Revision> gotRevisions = isolator.getRevisionsToRequest(projectConfig, project, true);
        assertEquals(1, gotRevisions.size());
        Revision got = gotRevisions.get(0);
        assertEquals(rev.getRevisionString(), got.getRevisionString());
        assertNotSame(rev, got);
    }

    private Revision returnLatestBuild(long revision)
    {
        Revision rev = new Revision(null, null, null, Long.toString(revision));
        mockScm.expectAndReturn("getLatestRevision", C.ANY_ARGS, rev);
        return rev;
    }

    private void returnNoMoreBuilds()
    {
        mockBuildManager.expectAndReturn("queryBuilds", C.ANY_ARGS, new LinkedList<BuildResult>());
    }

    private void returnNullDetails()
    {
        BuildResult result = new BuildResult();
        mockBuildManager.expectAndReturn("queryBuilds", C.ANY_ARGS, Arrays.asList(result));
    }

    private Revision returnBuild(long revision)
    {
        BuildResult result = new BuildResult();
        Revision rev = new Revision(null, null, null, Long.toString(revision));
        result.setRevision(rev);
        mockBuildManager.expectAndReturn("queryBuilds", C.ANY_ARGS, Arrays.asList(result));
        return rev;
    }

    private void returnRevisions(long since, long... revisions)
    {
        List<Revision> ret = new LinkedList<Revision>();
        for(long r: revisions)
        {
            ret.add(new Revision(null, null, null, Long.toString(r)));
        }

        mockScm.expectAndReturn("getRevisions", C.args(C.eq(new Revision(null, null, null, Long.toString(since))), C.IS_NULL), ret);
    }

    private void setupIsolator()
    {
        BuildManager buildManager = (BuildManager) mockBuildManager.proxy();
        scmClient = (ScmClient) mockScm.proxy();
        isolator = new ChangelistIsolator(buildManager);
        isolator.setScmClientFactory(new DelegateScmClientFactory()
        {
            public ScmClient createClient(ScmConfiguration config) throws ScmException
            {
                return scmClient;
            }
        });
    }

    private void expectRevisions(boolean force, long... revisions) throws ScmException
    {
        List<Revision> gotRevisions = isolator.getRevisionsToRequest(projectConfig, project, force);
        assertEquals(revisions.length, gotRevisions.size());
        for(int i = 0; i < revisions.length; i++)
        {
            assertEquals(revisions[i], (long)Long.valueOf(gotRevisions.get(i).getRevisionString()));
        }
    }


}
