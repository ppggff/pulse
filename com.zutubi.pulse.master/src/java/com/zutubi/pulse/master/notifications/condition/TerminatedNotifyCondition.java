package com.zutubi.pulse.master.notifications.condition;

import com.zutubi.pulse.master.model.BuildResult;
import com.zutubi.pulse.master.tove.config.user.UserConfiguration;

/**
 * Simple notify condition for terminated builds.
 */
public class TerminatedNotifyCondition implements NotifyCondition
{
    public boolean satisfied(BuildResult result, UserConfiguration user)
    {
        return result != null && result.terminated();
    }
}
