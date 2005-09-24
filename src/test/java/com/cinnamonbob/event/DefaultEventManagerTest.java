package com.cinnamonbob.event;

import junit.framework.TestCase;

import java.util.List;
import java.util.LinkedList;

/**
 * <class-comment/>
 */
public class DefaultEventManagerTest extends TestCase
{
    DefaultEventManager evtManager;

    RecordingEventListener listener;

    public DefaultEventManagerTest()
    {

    }

    public DefaultEventManagerTest(String testName)
    {
        super(testName);
    }

    public void setUp() throws Exception
    {
        super.setUp();
        
        evtManager = new DefaultEventManager(new SynchronousDispatcher());
        listener = new RecordingEventListener(new Class[]{Event.class});
    }

    public void tearDown() throws Exception
    {
        // tear down here.
        
        super.tearDown();
    }

    public void testRegisterListener()
    {
        evtManager.register(listener);

        assertEquals(0, listener.getEventsReceived().size());
        evtManager.publish(new Event(this));
        assertEquals(1, listener.getEventsReceived().size());
    }

    public void testMultipleRegistrationsBySingleListener()
    {
        evtManager.register(listener);
        evtManager.register(listener);
        evtManager.publish(new Event(this));
        assertEquals(1, listener.getEventsReceived().size());
    }

    public void testUnregisterListener()
    {
        evtManager.register(listener);
        evtManager.unregister(listener);
        evtManager.publish(new Event(this));
        assertEquals(0, listener.getEventsReceived().size());
    }

    public void testListenerRegisteredInCallbackDoesNotReceiveEvent()
    {
        evtManager.register(new MockEventListener(new Class[]{Event.class})
        {
            public void handleEvent(Event evt)
            {
                evtManager.register(listener);
            }
        });
        evtManager.publish(new Event(this));
        assertEquals(0, listener.getEventsReceived().size());
    }

    public void testListenerUnregisteredInCallbackStillReceivesEvent()
    {
        evtManager.register(new MockEventListener(new Class[]{Event.class})
        {
            public void handleEvent(Event evt)
            {
                // need to ensure that the listener we are removing was not
                // triggered before it was removed, otherwise this test is meaningless.
                assertEquals(0, listener.getEventsReceived().size());
                evtManager.unregister(listener);
            }
        });
        evtManager.register(listener);
        evtManager.publish(new Event(this));
        assertEquals(1, listener.getEventsReceived().size());
    }

    public void testPublishSuperClassOfRequestedEvent()
    {
        RecordingEventListener listener = new RecordingEventListener(new Class[]{TestEvent.class});

        evtManager.register(listener);
        evtManager.publish(new Event(this));
        assertEquals(0, listener.getReceivedCount());
        evtManager.publish(new TestEvent(this));
        assertEquals(1, listener.getReceivedCount());

        listener.reset();
        evtManager.unregister(listener);
        evtManager.publish(new Event(this));
        evtManager.publish(new TestEvent(this));
        assertEquals(0, listener.getReceivedCount());
    }

    public void testPublishSubClassOfRequestedEvent()
    {
        RecordingEventListener listener = new RecordingEventListener(new Class[]{Event.class});

        evtManager.register(listener);
        evtManager.publish(new Event(this));
        assertEquals(1, listener.getReceivedCount());
        evtManager.publish(new TestEvent(this));
        assertEquals(2, listener.getReceivedCount());

        listener.reset();
        evtManager.unregister(listener);
        evtManager.publish(new Event(this));
        evtManager.publish(new TestEvent(this));
        assertEquals(0, listener.getReceivedCount());
    }

    public void testReceiveAllEventsByDefault()
    {
        RecordingEventListener listener = new RecordingEventListener(new Class[]{});

        evtManager.register(listener);
        evtManager.publish(new Event(this));
        assertEquals(1, listener.getReceivedCount());
        evtManager.publish(new TestEvent(this));
        assertEquals(2, listener.getReceivedCount());

    }

    private class MockEventListener implements EventListener
    {
        private final Class[] handledEvents;

        public MockEventListener(Class[] handledEvents)
        {
            this.handledEvents = handledEvents;
        }

        public Class[] getHandledEvents()
        {
            return handledEvents;
        }

        public void handleEvent(Event evt)
        {

        }
    }

    private class RecordingEventListener extends MockEventListener
    {

        private final List<Event> events = new LinkedList<Event>();

        public RecordingEventListener(Class[] handledEvents)
        {
            super(handledEvents);
        }

        public void handleEvent(Event evt)
        {
            events.add(evt);
        }

        public List<Event> getEventsReceived()
        {
            return events;
        }

        public int getReceivedCount()
        {
            return getEventsReceived().size();
        }

        public void reset()
        {
            getEventsReceived().clear();
        }
    }

    private class TestEvent extends Event
    {
        public TestEvent(Object source)
        {
            super(source);
        }
    }

    private void pause(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            // noop
        }
    }
}
