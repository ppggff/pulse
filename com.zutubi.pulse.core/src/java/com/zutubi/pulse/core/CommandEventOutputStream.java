package com.zutubi.pulse.core;

import com.zutubi.events.EventManager;
import com.zutubi.pulse.core.events.CommandOutputEvent;

/**
 *
 *
 */
public class CommandEventOutputStream extends EventOutputStream
{
    private long recipeId;
    private EventManager eventManager;

    public CommandEventOutputStream(EventManager eventManager, long recipeId, boolean autoflush)
    {
        super(autoflush);
        this.recipeId = recipeId;
        this.eventManager = eventManager;
    }

    protected void sendEvent(byte[] sendBuffer)
    {
        CommandOutputEvent event = new CommandOutputEvent(this, recipeId, sendBuffer);
        eventManager.publish(event);
        offset = 0;
    }
}
