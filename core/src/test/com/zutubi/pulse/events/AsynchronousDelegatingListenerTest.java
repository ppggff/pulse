package com.zutubi.pulse.events;

import com.zutubi.pulse.test.PulseTestCase;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * <class-comment/>
 */
public class AsynchronousDelegatingListenerTest extends PulseTestCase
{
    private Semaphore eventSemaphore = new Semaphore(0);
    private Semaphore doneSemaphore = new Semaphore(0);

    public AsynchronousDelegatingListenerTest()
    {
    }

    public AsynchronousDelegatingListenerTest(String name)
    {
        super(name);
    }

    protected void setUp() throws Exception
    {
        super.setUp();
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    public void testEventsExecutedOnSeparateThread() throws InterruptedException
    {
        WatiListener delegate = new WatiListener();
        AsynchronousDelegatingListener l = new AsynchronousDelegatingListener(delegate);

        l.handleEvent(new Event<Object>(this));
        // the listener thread is now waiting for the semaphore to release.
        // we can only release it if it is indeed in a separate thread.
        eventSemaphore.release();

        assertTrue(doneSemaphore.tryAcquire(10, TimeUnit.SECONDS));
        assertTrue(delegate.acquired);
    }

    private class WatiListener implements EventListener
    {
        private boolean acquired;

        public void handleEvent(Event evt)
        {
            try
            {
                acquired = eventSemaphore.tryAcquire(10, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            doneSemaphore.release();
        }

        public Class[] getHandledEvents()
        {
            return new Class[]{Event.class};
        }
    }
}
