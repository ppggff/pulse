package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.pages.PulseToolbar;
import com.zutubi.pulse.acceptance.pages.browse.BuildSummaryPage;
import com.zutubi.pulse.acceptance.pages.browse.ProjectHomePage;
import com.zutubi.pulse.acceptance.pages.dashboard.MyBuildsPage;
import com.zutubi.pulse.acceptance.pages.dashboard.PersonalBuildSummaryPage;
import com.zutubi.pulse.acceptance.utils.*;
import com.zutubi.pulse.acceptance.utils.workspace.SubversionWorkspace;
import static com.zutubi.pulse.master.model.UserManager.DEVELOPERS_GROUP_NAME;
import static com.zutubi.pulse.master.tove.config.MasterConfigurationRegistry.GROUPS_SCOPE;
import static com.zutubi.pulse.master.tove.config.MasterConfigurationRegistry.USERS_SCOPE;
import com.zutubi.pulse.master.tove.config.user.UserConfiguration;
import com.zutubi.util.FileSystemUtils;

import java.io.File;
import java.util.Hashtable;
import java.util.Vector;

/**
 * Acceptance tests for the build navigation portion of the breadcrumbs.
 */
public class BuildNavigationAcceptanceTest extends SeleniumTestBase
{
    //NOTE: This acceptance test is structured slightly differently to avoid the
    //      creation of a heap of projects and builds.  It follows the workflow
    //      and testing of a single project.

    private BuildRunner buildRunner;
    private ConfigurationHelper configurationHelper;
    private ProjectConfigurations projects;
    private UserConfigurations users;
    private String projectName;
    private PulseToolbar toolbar;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        buildRunner = new BuildRunner(xmlRpcHelper);

        ConfigurationHelperFactory factory = new SingletonConfigurationHelperFactory();
        configurationHelper = factory.create(xmlRpcHelper);

        projects = new ProjectConfigurations(configurationHelper);
        users = new UserConfigurations();

        xmlRpcHelper.loginAsAdmin();

        toolbar = new PulseToolbar(browser);
    }

    @Override
    protected void tearDown() throws Exception
    {
        browser.logout();
        xmlRpcHelper.logout();

        super.tearDown();
    }

    public void testBuildNavigation() throws Exception
    {
        projectName = randomName() + "\"' ;?&<html>";

        // create project.
        ProjectConfigurationHelper project = projects.createTrivialAntProject(projectName);
        configurationHelper.insertProject(project.getConfig());

        browser.loginAsAdmin();

        doTestNoBuildForProjectHomepage();

        // run single build
        buildRunner.triggerSuccessfulBuild(project.getConfig());

        doTestSingleBuild();

        // run some more builds.
        buildRunner.triggerSuccessfulBuild(project.getConfig());
        buildRunner.triggerSuccessfulBuild(project.getConfig());
        buildRunner.triggerSuccessfulBuild(project.getConfig());
        buildRunner.triggerSuccessfulBuild(project.getConfig());
        buildRunner.triggerSuccessfulBuild(project.getConfig());

        doTestMultipleBuilds();

        doTestPopupMenu();
    }

    private void doTestNoBuildForProjectHomepage()
    {
        // go to project home page - ensure that the standard stuff is there.
        browser.openAndWaitFor(ProjectHomePage.class, projectName);

        assertTrue(toolbar.isProjectLinkPresent());
        assertFalse(toolbar.isBuildNavPresent());
        assertFalse(toolbar.isBuildNavMenuPresent());
    }

    private void doTestSingleBuild()
    {
        browser.openAndWaitFor(BuildSummaryPage.class, projectName, 1L);

        toolbar.waitForBuildNav();

        assertFalse(toolbar.isBuildNavMenuPresent());
        assertTrue(toolbar.isBuildNavLinkPresent(1));
    }

    private void doTestMultipleBuilds()
    {
        browser.openAndWaitFor(BuildSummaryPage.class, projectName, 3L);

        toolbar.waitForBuildNav();

        assertTrue(toolbar.isBuildNavMenuPresent());
        assertTrue(toolbar.isBuildNavItemPresent(1));
        assertTrue(toolbar.isBuildNavItemPresent(2));
        assertTrue(toolbar.isBuildNavLinkPresent(3));
        assertTrue(toolbar.isBuildNavItemPresent(4));
        assertTrue(toolbar.isBuildNavItemPresent(5));
        assertFalse(toolbar.isBuildNavItemPresent(6));

        // check navigation
        toolbar.clickBuildNavItem(5);
        BuildSummaryPage page = browser.createPage(BuildSummaryPage.class, projectName, 5L);
        page.waitFor();

        // wait for reload?
        toolbar.waitForBuildNav();

        assertTrue(toolbar.isBuildNavLinkPresent(5));
        assertFalse(toolbar.isBuildNavItemPresent(1));
        assertTrue(toolbar.isBuildNavItemPresent(6));
    }

    private void doTestPopupMenu()
    {
        browser.openAndWaitFor(BuildSummaryPage.class, projectName, 3L);

        toolbar.waitForBuildNav();

        assertFalse(toolbar.isNextSuccessfulBuildLinkPresent());

        assertTrue(toolbar.isBuildNavMenuPresent());
        toolbar.clickOnNavMenu();

        assertTrue(toolbar.isNextSuccessfulBuildLinkPresent());
        assertTrue(toolbar.isPreviousSuccessfulBuildLinkPresent());
        assertFalse(toolbar.isNextBrokenBuildLinkPresent());
        assertFalse(toolbar.isPreviousBrokenBuildLinkPresent());

        browser.openAndWaitFor(BuildSummaryPage.class, projectName, 1L);

        toolbar.waitForBuildNav();
        toolbar.clickOnNavMenu();
        assertTrue(toolbar.isNextSuccessfulBuildLinkPresent());
        assertFalse(toolbar.isPreviousSuccessfulBuildLinkPresent());

        toolbar.clickNextSuccessfulBuildLink();
        BuildSummaryPage page = browser.createPage(BuildSummaryPage.class, projectName, 2L);
        page.waitFor();

        toolbar.waitForBuildNav();
        assertTrue(toolbar.isBuildNavLinkPresent(2));

        toolbar.clickOnNavMenu();
        assertTrue(toolbar.isNextSuccessfulBuildLinkPresent());
        assertTrue(toolbar.isPreviousSuccessfulBuildLinkPresent());
    }

    public void testPersonalBuildNavigation() throws Exception
    {
        // create project and user.
        projectName = randomName();

        ProjectConfigurationHelper project = projects.createTrivialAntProject(projectName);
        configurationHelper.insertProject(project.getConfig());

        String userName = randomName();
        UserConfiguration user = users.createSimpleUser(userName);
        configurationHelper.insertUser(user);

        // user needs 'run personal build' permissions.
        ensureUserCanRunPersonalBuild(userName);

        browser.login(userName, "");
        xmlRpcHelper.login(userName, "");

        File workingCopy = createTempDirectory();
        SubversionWorkspace workspace = new SubversionWorkspace(workingCopy, "pulse", "pulse");
        workspace.doCheckout(Constants.TRIVIAL_ANT_REPOSITORY);

        // make a change to the working copy so that we can run personal builds.
        File newFile = new File(workingCopy, "file.txt");
        FileSystemUtils.createFile(newFile, "new file");
        workspace.doAdd(newFile);

        PersonalBuildRunner buildRunner = new PersonalBuildRunner(xmlRpcHelper);
        buildRunner.setBase(workingCopy);
        buildRunner.createConfigFile(browser.getBaseUrl(), userName, "", projectName);

        doTestNoPersonalBuilds();

        buildRunner.triggerAndWaitForBuild();

        doTestSinglePersonalBuild();

        buildRunner.triggerAndWaitForBuild();
        buildRunner.triggerAndWaitForBuild();
        buildRunner.triggerAndWaitForBuild();
        buildRunner.triggerAndWaitForBuild();

        // wait for personal builds to complete.

        doTestMultiplePersonalBuilds();

        doTestPersonalPopupMenu();
    }

    private void doTestNoPersonalBuilds()
    {
        browser.openAndWaitFor(MyBuildsPage.class);

        assertTrue(toolbar.isMyBuildsLinkPresent());
        assertFalse(toolbar.isBuildNavPresent());
        assertFalse(toolbar.isBuildNavMenuPresent());
    }

    private void doTestSinglePersonalBuild()
    {
        browser.openAndWaitFor(PersonalBuildSummaryPage.class, 1L);

        toolbar.waitForBuildNav();

        assertFalse(toolbar.isBuildNavMenuPresent());
        assertTrue(toolbar.isBuildNavLinkPresent(1));
    }

    private void doTestMultiplePersonalBuilds()
    {
        browser.openAndWaitFor(PersonalBuildSummaryPage.class, 3L);

        toolbar.waitForBuildNav();

        assertTrue(toolbar.isBuildNavMenuPresent());
        assertTrue(toolbar.isBuildNavItemPresent(1));
        assertTrue(toolbar.isBuildNavItemPresent(2));
        assertTrue(toolbar.isBuildNavLinkPresent(3));
        assertTrue(toolbar.isBuildNavItemPresent(4));
        assertTrue(toolbar.isBuildNavItemPresent(5));

        // check navigation
        toolbar.clickBuildNavItem(5);
        PersonalBuildSummaryPage page = browser.createPage(PersonalBuildSummaryPage.class, 5L);
        page.waitFor();

        toolbar.waitForBuildNav();

        assertTrue(toolbar.isBuildNavLinkPresent(5));
        assertTrue(toolbar.isBuildNavItemPresent(3));
    }

    private void doTestPersonalPopupMenu()
    {
        browser.openAndWaitFor(PersonalBuildSummaryPage.class, 3L);

        toolbar.waitForBuildNav();

        assertFalse(toolbar.isNextSuccessfulBuildLinkPresent());

        assertTrue(toolbar.isBuildNavMenuPresent());
        toolbar.clickOnNavMenu();

        assertTrue(toolbar.isNextSuccessfulBuildLinkPresent());
        assertTrue(toolbar.isPreviousSuccessfulBuildLinkPresent());
        assertFalse(toolbar.isNextBrokenBuildLinkPresent());
        assertFalse(toolbar.isPreviousBrokenBuildLinkPresent());

        browser.openAndWaitFor(PersonalBuildSummaryPage.class, 1L);
        toolbar.waitForBuildNav();
        toolbar.clickOnNavMenu();

        assertTrue(toolbar.isNextSuccessfulBuildLinkPresent());
        assertFalse(toolbar.isPreviousSuccessfulBuildLinkPresent());

        toolbar.clickNextSuccessfulBuildLink();
        BuildSummaryPage page = browser.createPage(PersonalBuildSummaryPage.class, 2L);
        page.waitFor();

        toolbar.waitForBuildNav();
        assertTrue(toolbar.isBuildNavLinkPresent(2));

        toolbar.clickOnNavMenu();
        assertTrue(toolbar.isNextSuccessfulBuildLinkPresent());
        assertTrue(toolbar.isPreviousSuccessfulBuildLinkPresent());
    }

    private void ensureUserCanRunPersonalBuild(String userName) throws Exception
    {
        String groupPath = GROUPS_SCOPE + "/" + DEVELOPERS_GROUP_NAME;
        Hashtable group = xmlRpcHelper.getConfig(groupPath);
        Vector members = (Vector) group.get("members");

        String userReference = USERS_SCOPE + "/" + userName;
        if (!members.contains(userReference))
        {
            members.add(userReference);
            xmlRpcHelper.saveConfig(groupPath, group, false);
        }
    }
}
