package com.zutubi.pulse.prototype.config.project.triggers;

import com.zutubi.pulse.core.config.AbstractNamedConfiguration;
import com.zutubi.pulse.scheduling.Trigger;
import com.zutubi.config.annotations.Internal;

/**
 * 
 */
public abstract class TriggerConfiguration extends AbstractNamedConfiguration
{
    @Internal
    private long triggerId;

    public long getTriggerId()
    {
        return triggerId;
    }

    public void setTriggerId(long triggerId)
    {
        this.triggerId = triggerId;
    }

    public abstract Trigger newTrigger();

    public abstract void update(Trigger trigger);
}
