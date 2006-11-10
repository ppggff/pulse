package com.zutubi.pulse.web.admin;

import com.zutubi.pulse.model.NamedEntityComparator;
import com.zutubi.pulse.model.Project;
import com.zutubi.pulse.model.ProjectManager;
import com.zutubi.pulse.committransformers.CommitMessageTransformerManager;
import com.zutubi.pulse.web.ActionSupport;

import java.util.*;

/**
 */
public class CommitMessageTransformerActionSupport extends ActionSupport
{
    private long id;
    private CommitMessageTransformerManager transformerManager;
    private ProjectManager projectManager;
    private Map<Long, String> allProjects;
    private List<Long> selectedProjects = new LinkedList<Long>();

    public long getId()
    {
        return id;
    }

    public void setId(long id)
    {
        this.id = id;
    }

    public Map<Long, String> getAllProjects()
    {
        if(allProjects == null)
        {
            List<Project> projects = projectManager.getAllProjects();
            Collections.sort(projects, new NamedEntityComparator());
            allProjects = new LinkedHashMap<Long, String>();
            for(Project p: projects)
            {
                allProjects.put(p.getId(), p.getName());
            }
        }
        return allProjects;
    }

    public List<Long> getSelectedProjects()
    {
        return selectedProjects;
    }

    public void setSelectedProjects(List<Long> selectedProjects)
    {
        this.selectedProjects = selectedProjects;
    }

    public ProjectManager getProjectManager()
    {
        return projectManager;
    }

    public CommitMessageTransformerManager getTransformerManager()
    {
        return transformerManager;
    }

    public void setCommitMessageTransformerManager(CommitMessageTransformerManager transformerManager)
    {
        this.transformerManager = transformerManager;
    }

    public void setProjectManager(ProjectManager projectManager)
    {
        this.projectManager = projectManager;
    }
}
