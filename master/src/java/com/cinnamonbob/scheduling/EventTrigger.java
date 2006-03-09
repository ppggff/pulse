package com.cinnamonbob.scheduling;

import com.cinnamonbob.events.Event;
import com.cinnamonbob.core.ObjectFactory;
import com.cinnamonbob.util.logging.Logger;

/**
 * The EventTrigger is triggered by the occurance of an event within the system.
 * Which event will trigger the event trigger is defined by the triggerEvents property.
 *
 */
public class EventTrigger extends Trigger
{
    static final String TYPE = "event";

    private static final Class<Event>[] DEFAULT_TRIGGER_EVENTS = new Class[]{Event.class};
    private Class<Event>[] triggers = DEFAULT_TRIGGER_EVENTS;
    private Class<? extends EventTriggerFilter> filterClass = null;

    /**
     * Default no argument constructor required by hibernate. 
     */
    public EventTrigger()
    {

    }

    public EventTrigger(Class trigger)
    {
        this(trigger, null);
    }

    public EventTrigger(Class trigger, String name)
    {
        this(trigger, name, DEFAULT_GROUP);
    }

    public EventTrigger(Class trigger, String name, Class<? extends EventTriggerFilter> filterClass)
    {
        this(trigger, name, DEFAULT_GROUP);
        this.filterClass = filterClass;
    }

    public EventTrigger(Class trigger, String name, String group)
    {
        super(name, group);
        triggers = new Class[]{trigger};
    }

    public EventTrigger(Class trigger, String name, String group, Class<? extends EventTriggerFilter> filterClass)
    {
        super(name, group);
        triggers = new Class[]{trigger};
        this.filterClass = filterClass;
    }

    public String getType()
    {
        return TYPE;
    }

    /**
     * Get the array of Event classes that will trigger this event trigger.
     *
     * @return the array of event classes.
     */
    public Class<Event>[] getTriggerEvents()
    {
        return triggers;
    }

    private Class getTriggerEvent()
    {
        return getTriggerEvents()[0];
    }

    private void setTriggerEvent(Class event)
    {
        getTriggerEvents()[0] = event;
    }

    public Class<? extends EventTriggerFilter> getFilterClass()
    {
        return filterClass;
    }

    public void setFilterClass(Class<? extends EventTriggerFilter> filterClass)
    {
        this.filterClass = filterClass;
    }
}
