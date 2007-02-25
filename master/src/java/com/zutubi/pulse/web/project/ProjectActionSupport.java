package com.zutubi.pulse.web.project;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.committransformers.CommitMessageTransformerManager;
import com.zutubi.pulse.core.model.Feature;
import com.zutubi.pulse.model.*;
import com.zutubi.pulse.scheduling.Scheduler;
import com.zutubi.pulse.web.ActionSupport;
import org.acegisecurity.AccessDeniedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 
 *
 */
public class ProjectActionSupport extends ActionSupport
{
    protected BuildManager buildManager;
    protected ScmManager scmManager;
    protected UserManager userManager;
    protected Scheduler scheduler;

    private static final long NONE_SPECIFIED = -1;

    private User loggedInUser = null;
    protected long projectId = NONE_SPECIFIED;
    protected String projectName = null;

    public BuildManager getBuildManager()
    {
        return buildManager;
    }

    public void setBuildManager(BuildManager buildManager)
    {
        this.buildManager = buildManager;
    }

    public void setScmManager(ScmManager scmManager)
    {
        this.scmManager = scmManager;
    }

    public ScmManager getScmManager()
    {
        return scmManager;
    }

    public Scheduler getScheduler()
    {
        return this.scheduler;
    }

    public void setScheduler(Scheduler scheduler)
    {
        this.scheduler = scheduler;
    }

    public Feature.Level getErrorLevel()
    {
        return Feature.Level.ERROR;
    }

    public Feature.Level getWarningLevel()
    {
        return Feature.Level.WARNING;
    }

    public List<Feature.Level> getFeatureLevels()
    {
        List<Feature.Level> list = Arrays.asList(Feature.Level.values());
        Collections.reverse(list);
        return list;
    }

    public long getProjectId()
    {
        return projectId;
    }

    public void setProjectId(long projectId)
    {
        this.projectId = projectId;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public void setProjectName(String projectName)
    {
        this.projectName = projectName;
    }

    public Project getProject()
    {
        if (projectId != NONE_SPECIFIED)
        {
            return getProject(projectId);
        }
        else if (TextUtils.stringSet(projectName))
        {
            return getProject(projectName);
        }
        return null;
    }

    protected Project getProject(long id)
    {
        return projectManager.getProject(id);
    }

    protected Project getProject(String projectName)
    {
        return getProjectManager().getProject(projectName);
    }

    public void addUnknownProjectActionError()
    {
        if (projectId != NONE_SPECIFIED)
        {
            addActionError("Unknown project [" + projectId + "]");
        }
        else if (TextUtils.stringSet(projectName))
        {
            addActionError("Unknown project [" + projectName + "]");
        }
        else
        {
            addActionError("Require either a project name or id.");
        }
    }

    public void addUnknownProjectFieldError()
    {
        if (projectId != NONE_SPECIFIED)
        {
            addFieldError("projectId", "Unknown project [" + projectId + "]");
        }
        else if (TextUtils.stringSet(projectName))
        {
            addFieldError("projectName", "Unknown project [" + projectName + "]");
        }
        else
        {
            addActionError("Require either a project name or id.");
        }
    }

    public Project lookupProject(long id)
    {
        Project p = projectManager.getProject(id);
        if(p == null)
        {
            addActionError("Unknown project [" + id + "]");
        }

        return p;
    }

    public User getLoggedInUser()
    {
        if (loggedInUser == null)
        {
            Object principle = getPrinciple();
            if(principle != null && principle instanceof String)
            {
                loggedInUser = userManager.getUser((String)principle);
            }
        }

        return loggedInUser;
    }

    public void checkPermissions(BuildResult result)
    {
        if(result.isPersonal())
        {
            User user = getLoggedInUser();
            if(!result.getUser().equals(user))
            {
                throw new AccessDeniedException("Only the owner can view a personal build");
            }
        }
    }

    public void setUserManager(UserManager userManager)
    {
        this.userManager = userManager;
    }

    public CommitMessageTransformerManager getTransformerManager()
    {
        return commitMessageTransformerManager;
    }
}
