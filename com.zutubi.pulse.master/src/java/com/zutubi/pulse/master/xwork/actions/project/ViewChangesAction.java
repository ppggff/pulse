package com.zutubi.pulse.master.xwork.actions.project;

import com.zutubi.pulse.core.engine.api.ResultState;
import com.zutubi.pulse.core.model.ChangelistComparator;
import com.zutubi.pulse.core.model.PersistentChangelist;
import com.zutubi.pulse.core.model.PersistentFileChange;
import com.zutubi.pulse.master.committransformers.CommitMessageSupport;
import com.zutubi.pulse.master.model.BuildResult;
import com.zutubi.pulse.master.model.DependencyManager;
import com.zutubi.pulse.master.model.Project;
import com.zutubi.pulse.master.model.UpstreamChangelist;
import com.zutubi.pulse.master.tove.config.project.changeviewer.ChangeViewerConfiguration;
import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Mapping;

import java.util.*;

/**
 */
public class ViewChangesAction extends BuildActionBase
{
    private static final int FILE_LIMIT = 5;

    private long sinceBuild = 0;
    private BuildResult previous;
    private BuildResult previousSuccessful;
    private BuildResult previousUnsuccessful;
    private BuildResult sinceResult;
    private String changeUrl;
    private List<ChangelistModel> changelists;
    private List<ChangelistModel> upstreamChangelists;
    
    private DependencyManager dependencyManager;
    
    public long getSinceBuild()
    {
        return sinceBuild;
    }

    public void setSinceBuild(long sinceBuild)
    {
        this.sinceBuild = sinceBuild;
    }

    public BuildResult getSinceResult()
    {
        return sinceResult;
    }

    public BuildResult getPrevious()
    {
        return previous;
    }

    public BuildResult getPreviousSuccessful()
    {
        return previousSuccessful;
    }

    public BuildResult getPreviousUnsuccessful()
    {
        return previousUnsuccessful;
    }

    public String getChangeUrl()
    {
        return changeUrl;
    }

    public void updateChangeUrl(ChangelistModel model)
    {
        changeUrl = null;
        if (model != null && model.getChangelist().getRevision() != null)
        {
            Project project = model.getPreferredProject();
            ChangeViewerConfiguration changeViewer = project.getConfig().getChangeViewer();
            if (changeViewer != null)
            {
                changeUrl = changeViewer.getRevisionURL(model.getChangelist().getRevision());
            }
        }
    }

    public CommitMessageSupport getCommitMessageSupport(ChangelistModel model)
    {
        return new CommitMessageSupport(model.getChangelist().getComment(), model.getPreferredProject().getConfig().getCommitMessageTransformers().values());
    }

    public List<ChangelistModel> getChangelists()
    {
        return changelists;
    }

    public List<ChangelistModel> getUpstreamChangelists()
    {
        return upstreamChangelists;
    }

    public String execute()
    {
        BuildResult result = getRequiredBuildResult();
        if(result.isPersonal())
        {
            return "personal";
        }

        previous = buildManager.getPreviousBuildResultWithRevision(result, ResultState.getCompletedStates());
        if(sinceBuild == 0)
        {
            sinceResult = previous;
            if(previous != null)
            {
                sinceBuild = sinceResult.getNumber();
            }
        }
        else
        {
            if(sinceBuild >= result.getNumber())
            {
                addActionError("Invalid build range");
                return ERROR;
            }

            sinceResult = buildManager.getByProjectAndNumber(result.getProject(), sinceBuild);
            if(sinceResult == null)
            {
                addActionError("No such build [" + sinceBuild + "]");
                return ERROR;
            }
        }

        List<PersistentChangelist> rawChangelists = buildManager.getChangesForBuild(result, sinceBuild, true);
        final ChangelistComparator changelistComparator = new ChangelistComparator();
        Collections.sort(rawChangelists, changelistComparator);
        changelists = CollectionUtils.map(rawChangelists, new Mapping<PersistentChangelist, ChangelistModel>()
        {
            public ChangelistModel map(PersistentChangelist persistentChangelist)
            {
                return new ChangelistModel(persistentChangelist, buildManager.getChangelistSize(persistentChangelist), buildManager.getChangelistFiles(persistentChangelist, 0, FILE_LIMIT));
            }
        });
        
        List<UpstreamChangelist> rawUpstreamChangelists = dependencyManager.getUpstreamChangelists(result, sinceResult);
        Collections.sort(rawUpstreamChangelists, new Comparator<UpstreamChangelist>()
        {
            public int compare(UpstreamChangelist o1, UpstreamChangelist o2)
            {
                return changelistComparator.compare(o1.getChangelist(), o2.getChangelist());
            }
        });
        
        upstreamChangelists = CollectionUtils.map(rawUpstreamChangelists, new Mapping<UpstreamChangelist, ChangelistModel>()
        {
            public ChangelistModel map(UpstreamChangelist upstreamChangelist)
            {
                PersistentChangelist persistentChangelist = upstreamChangelist.getChangelist();
                return new ChangelistModel(persistentChangelist, buildManager.getChangelistSize(persistentChangelist), buildManager.getChangelistFiles(persistentChangelist, 0, FILE_LIMIT), upstreamChangelist.getUpstreamContexts());
            }
        });
        
        previousSuccessful = buildManager.getPreviousBuildResultWithRevision(result, ResultState.getHealthyStates());
        previousUnsuccessful = buildManager.getPreviousBuildResultWithRevision(result, ResultState.getBrokenStates());

        return SUCCESS;
    }

    public void setDependencyManager(DependencyManager dependencyManager)
    {
        this.dependencyManager = dependencyManager;
    }

    public class ChangelistModel
    {
        private PersistentChangelist changelist;
        private int changeCount;
        private List<PersistentFileChange> changes;
        private List<List<BuildResult>> upstreamContexts = new LinkedList<List<BuildResult>>();
        
        public ChangelistModel(PersistentChangelist changelist, int changeCount, List<PersistentFileChange> changes)
        {
            this(changelist, changeCount, changes, Collections.<List<BuildResult>>emptyList());
        }

        public ChangelistModel(PersistentChangelist changelist, int changeCount, List<PersistentFileChange> changes, List<List<BuildResult>> upstreamContexts)
        {
            this.changelist = changelist;
            this.changeCount = changeCount;
            this.changes = changes;
            this.upstreamContexts.addAll(upstreamContexts);
        }

        public Project getPreferredProject()
        {
            return getPreferredBuild().getProject();
        }
        
        public BuildResult getPreferredBuild()
        {
            if (upstreamContexts.isEmpty())
            {
                return getBuildResult();
            }
            else
            {
                List<BuildResult> firstContext = upstreamContexts.get(0);
                return firstContext.get(firstContext.size() - 1);
            }
        }
        
        public PersistentChangelist getChangelist()
        {
            return changelist;
        }

        public int getChangeCount()
        {
            return changeCount;
        }

        public List<PersistentFileChange> getChanges()
        {
            return changes;
        }

        public List<List<BuildResult>> getUpstreamContexts()
        {
            return upstreamContexts;
        }
    }
}
