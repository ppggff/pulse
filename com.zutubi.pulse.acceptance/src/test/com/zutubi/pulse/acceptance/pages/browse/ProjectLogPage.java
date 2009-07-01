package com.zutubi.pulse.acceptance.pages.browse;

import com.zutubi.pulse.acceptance.SeleniumBrowser;
import com.zutubi.pulse.master.webwork.Urls;
import com.zutubi.util.StringUtils;

/**
 * The browse project log page.
 */
public class ProjectLogPage extends AbstractLogPage
{
    private String projectName;

    public ProjectLogPage(SeleniumBrowser browser, Urls urls, String projectName)
    {
        super(browser, urls, "project-log-" + StringUtils.uriComponentEncode(projectName));
        this.projectName = projectName;
    }

    public String getUrl()
    {
        return urls.projectLog(projectName);
    }
}
