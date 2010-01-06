package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.pages.admin.HibernateStatisticsPage;

/**
 * A sanity check to ensure that the hibernate statistics are displayable.
 */
public class HibernateStatisticsAcceptanceTest extends SeleniumTestBase
{
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        loginAsAdmin();
    }

    @Override
    protected void tearDown() throws Exception
    {
        logout();
        super.tearDown();
    }

    public void testCanViewStatistics() throws Exception
    {
        HibernateStatisticsPage statsPage = new HibernateStatisticsPage(selenium, urls);
        assertTrue(statsPage.isPresent());
        assertFalse(statsPage.isEnabled());

        statsPage.clickToggle();
        statsPage.waitFor();

        assertTrue(statsPage.isEnabled());

        statsPage.clickToggle();
        statsPage.waitFor();

        assertFalse(statsPage.isEnabled());
    }

}