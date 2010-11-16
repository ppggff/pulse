package com.zutubi.pulse.acceptance;

import com.zutubi.pulse.acceptance.pages.LoginPage;

import static com.zutubi.pulse.acceptance.AcceptanceTestUtils.ADMIN_CREDENTIALS;
import static org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY;

public class RememberMeAcceptanceTest extends AcceptanceTestBase
{
    private static final String USERNAME = ADMIN_CREDENTIALS.getUserName();
    private static final String PASSWORD = ADMIN_CREDENTIALS.getPassword();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        getBrowser().deleteAllCookies();
    }

    public void testLoginWithRememberMe()
    {
        assertFalse(isRememberMeCookieSet());
        LoginPage page = getBrowser().openAndWaitFor(LoginPage.class);
        assertTrue(page.login(USERNAME, PASSWORD, true));
        assertTrue(isRememberMeCookieSet());
    }

    public void testLoginWithoutRememberMe()
    {
        assertFalse(isRememberMeCookieSet());
        LoginPage page = getBrowser().openAndWaitFor(LoginPage.class);
        assertTrue(page.login(USERNAME, PASSWORD, false));
        assertFalse(isRememberMeCookieSet());
    }

    public void testCookieClearedAfterLogout()
    {
        assertFalse(isRememberMeCookieSet());

        LoginPage page = getBrowser().openAndWaitFor(LoginPage.class);
        assertTrue(page.login(USERNAME, PASSWORD, true));
        assertTrue(isRememberMeCookieSet());

        getBrowser().logout();
        assertFalse(isRememberMeCookieSet());
    }

    public void testThatTheRememberMeCookieWorksAsAdvertised()
    {
        assertFalse(isRememberMeCookieSet());

        // login to get ourselves a valid remember me cookie.
        LoginPage loginPage = getBrowser().openAndWaitFor(LoginPage.class);
        assertTrue(loginPage.login(USERNAME, PASSWORD, true));
        assertTrue(isRememberMeCookieSet());

        String cookie = getBrowser().getCookie(SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);

        getBrowser().setCaptureNetworkTraffic(true);
        getBrowser().newSession();
        getBrowser().deleteAllCookies();

        // open the browser at '/' and ensure we are asked to login.
        getBrowser().open("/");
        getBrowser().waitForPageToLoad();
        assertTrue("Login page is expected.", loginPage.isPresent());
        getBrowser().resetCapturedNetworkTraffic();
        getBrowser().setCookie(SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY, cookie);
        getBrowser().open("/");
        getBrowser().waitForPageToLoad();
        assertFalse("Login page is not expected.", loginPage.isPresent());

        System.out.println(getBrowser().getCapturedNetworkTraffic());
    }

    private boolean isRememberMeCookieSet()
    {
        return getBrowser().isCookiePresent(SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
    }
}
