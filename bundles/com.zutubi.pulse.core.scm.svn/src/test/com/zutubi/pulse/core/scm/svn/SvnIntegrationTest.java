package com.zutubi.pulse.core.scm.svn;

import com.zutubi.pulse.core.scm.ScmException;
import com.zutubi.pulse.core.scm.AbstractScmIntegrationTestCase;
import com.zutubi.pulse.core.scm.NumericalRevision;
import com.zutubi.pulse.core.scm.ExpectedTestResults;
import com.zutubi.pulse.core.model.Revision;

import java.util.List;
import java.util.Arrays;
import java.text.ParseException;
import java.io.IOException;

public class SvnIntegrationTest extends AbstractScmIntegrationTestCase
{
//    private ScmClient client;

//    private ExpectedTestResults testData;

    private static final Revision FIRST_REVISION = SvnClient.convertRevision(new NumericalRevision(2));
    private static final Revision SECOND_REVISION = SvnClient.convertRevision(new NumericalRevision(3));
    private static final Revision THIRD_REVISION = SvnClient.convertRevision(new NumericalRevision(4));
    private static final Revision FOURTH_REVISION = SvnClient.convertRevision(new NumericalRevision(5));
    private static final Revision FIFTH_REVISION = SvnClient.convertRevision(new NumericalRevision(6));

    protected void setUp() throws Exception
    {
        super.setUp();

        client = new SvnClient("svn+ssh://cinnamonbob.com/svnroots/svn-1.2.3/integration-test/trunk", "dostermeier", "4edueWX7");

        List<Revision> revisions = Arrays.asList(FIRST_REVISION, SECOND_REVISION, THIRD_REVISION, FOURTH_REVISION, FIFTH_REVISION);
        // installation directory is integration-test, this is independent of the module being worked with.
        testData = new ExpectedTestResults(revisions);
        testData.setVersionDirectorySupport(true);
        prefix = "";
    }

    protected void tearDown() throws Exception
    {
        client = null;
        prefix = null;

        super.tearDown();
    }

    public void testPrepareACleanDirectory() throws ScmException, ParseException
    {
        super.testPrepareACleanDirectory();
    }

    public void testPrepareAnExistingDirectory() throws ScmException
    {
        super.testPrepareAnExistingDirectory();
    }

    public void testRetrieveFile() throws ScmException, IOException
    {
        super.testRetrieveFile();
    }

    public void testRetrieveFileOfSpecifiedRevision() throws ScmException, IOException
    {
        super.testRetrieveFileOfSpecifiedRevision();
    }

    public void testRetrieveDirectory() throws ScmException, IOException
    {
        super.testRetrieveDirectory();
    }

    public void testRetrieveNonExistantFile() throws ScmException
    {
        super.testRetrieveNonExistantFile();
    }

    public void testBrowse() throws ScmException
    {
        super.testBrowse();
    }

    public void testAttemptingToBrowseFile() throws ScmException
    {
        super.testAttemptingToBrowseFile();
    }

    public void testGetLatestRevision() throws ScmException
    {
        super.testGetLatestRevision();
    }

    public void testGetRevisionsFromXToY() throws ScmException
    {
        super.testGetRevisionsFromXToY();
    }

    public void testGetRevisionsFromX() throws ScmException
    {
        super.testGetRevisionsFromX();
    }

    public void testChangesetFromXToY() throws ScmException
    {
        super.testChangesetFromXToY();
    }

    public void testChangesetFromX() throws ScmException
    {
        super.testChangesetFromX();
    }
}
