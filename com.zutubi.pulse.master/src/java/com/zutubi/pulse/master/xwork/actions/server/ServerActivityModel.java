/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.master.xwork.actions.server;

import com.zutubi.pulse.core.scm.api.Revision;
import com.zutubi.pulse.master.build.queue.ActivatedRequest;
import com.zutubi.pulse.master.events.build.BuildRequestEvent;
import com.zutubi.pulse.master.events.build.PersonalBuildRequestEvent;
import com.zutubi.pulse.master.model.BuildResult;
import com.zutubi.pulse.master.model.BuildRevision;
import com.zutubi.pulse.master.webwork.Urls;
import com.zutubi.pulse.master.xwork.actions.project.BuildModel;
import com.zutubi.pulse.master.xwork.actions.project.RevisionModel;

import java.util.LinkedList;
import java.util.List;

/**
 * Models JSON data for the server activity tab.
 */
public class ServerActivityModel
{
    private boolean buildQueueTogglePermitted;
    private boolean buildQueueRunning;
    private boolean stageQueueTogglePermitted;
    private boolean stageQueueRunning;
    private boolean cancelAllPermitted;
    private List<QueuedBuildModel> queued = new LinkedList<QueuedBuildModel>();
    private List<ActiveBuildModel> active = new LinkedList<ActiveBuildModel>();

    public boolean isBuildQueueTogglePermitted()
    {
        return buildQueueTogglePermitted;
    }

    public void setBuildQueueTogglePermitted(boolean buildQueueTogglePermitted)
    {
        this.buildQueueTogglePermitted = buildQueueTogglePermitted;
    }

    public boolean isBuildQueueRunning()
    {
        return buildQueueRunning;
    }

    public void setBuildQueueRunning(boolean buildQueueRunning)
    {
        this.buildQueueRunning = buildQueueRunning;
    }

    public boolean isStageQueueTogglePermitted()
    {
        return stageQueueTogglePermitted;
    }

    public void setStageQueueTogglePermitted(boolean stageQueueTogglePermitted)
    {
        this.stageQueueTogglePermitted = stageQueueTogglePermitted;
    }

    public boolean isStageQueueRunning()
    {
        return stageQueueRunning;
    }

    public void setStageQueueRunning(boolean stageQueueRunning)
    {
        this.stageQueueRunning = stageQueueRunning;
    }

    public boolean isCancelAllPermitted()
    {
        return cancelAllPermitted;
    }

    public void setCancelAllPermitted(boolean cancelAllPermitted)
    {
        this.cancelAllPermitted = cancelAllPermitted;
    }

    public List<QueuedBuildModel> getQueued()
    {
        return queued;
    }

    public void addQueued(QueuedBuildModel model)
    {
        queued.add(model);
    }

    public List<ActiveBuildModel> getActive()
    {
        return active;
    }

    public void addActive(ActiveBuildModel model)
    {
        active.add(model);
    }

    public static class QueuedBuildModel
    {
        private long id;
        private String owner;
        private boolean personal;
        private long personalNumber;
        private RevisionModel revision;
        private String prettyQueueTime;
        private String reason;
        private boolean cancelPermitted;
        private boolean hidden;
        
        public QueuedBuildModel(BuildRequestEvent requestEvent, boolean cancelPermitted)
        {
            id = requestEvent.getId();
            owner = requestEvent.getOwner().getName();

            if (requestEvent instanceof PersonalBuildRequestEvent)
            {
                personal = true;
                personalNumber = ((PersonalBuildRequestEvent) requestEvent).getNumber();
                revision = new RevisionModel("[personal]");
            }
            else
            {
                personal = false;
                Revision revision = requestEvent.getRevision().getRevision();
                if (revision == null)
                {
                    this.revision = new RevisionModel("[floating]");
                }
                else
                {
                    this.revision = new RevisionModel(revision.getRevisionString());
                }
            }

            prettyQueueTime = requestEvent.getPrettyQueueTime();
            reason = requestEvent.getReason().getSummary();
            this.cancelPermitted = cancelPermitted;
            hidden = false;
        }

        public QueuedBuildModel(boolean personal)
        {
            this.personal = personal;
            hidden = true;
        }

        public long getId()
        {
            return id;
        }

        public String getOwner()
        {
            return owner;
        }

        public boolean isPersonal()
        {
            return personal;
        }

        public long getPersonalNumber()
        {
            return personalNumber;
        }

        public RevisionModel getRevision()
        {
            return revision;
        }

        public String getPrettyQueueTime()
        {
            return prettyQueueTime;
        }

        public String getReason()
        {
            return reason;
        }

        public boolean isCancelPermitted()
        {
            return cancelPermitted;
        }

        public boolean isHidden()
        {
            return hidden;
        }
    }

    public static class ActiveBuildModel extends BuildModel
    {
        private boolean hidden;

        public ActiveBuildModel(ActivatedRequest request, BuildResult buildResult, Urls urls, boolean cancelPermitted)
        {
            super(buildResult, urls, false);
            setCancelPermitted(cancelPermitted);
            hidden = false;
            // CIB-3330: if the build itself has not yet got a revision, check if the request has a
            // fixed revision (which will be used by the build on commencement).
            if (getRevision() == null)
            {
                BuildRevision requestRevision = request.getRequest().getRevision();
                if (requestRevision.isInitialised())
                {
                    setRevision(new RevisionModel(requestRevision.getRevision(), buildResult.getProject().getConfig()));
                }
            }
        }

        public ActiveBuildModel(boolean personal)
        {
            super(0, 0, personal, null, null, null, null, null, null, null, null);
            hidden = true;
        }

        public boolean isHidden()
        {
            return hidden;
        }
    }
}
