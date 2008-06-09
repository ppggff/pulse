package com.zutubi.pulse.agent;

import com.zutubi.pulse.Version;
import com.zutubi.pulse.core.Stoppable;
import com.zutubi.pulse.events.AgentPingEvent;
import com.zutubi.pulse.events.EventManager;
import com.zutubi.pulse.services.SlaveService;
import com.zutubi.pulse.services.SlaveStatus;
import com.zutubi.pulse.services.TokenManager;
import com.zutubi.pulse.util.logging.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the task of pinging agents and sending out the results as events.
 * Duplicate ping requests are filtered, and ping timeouts are managed (ping
 * results are sent by the timeout even if the pinging thread is still
 * awaiting a response from a remote agent).
 */
public class AgentPingService implements Stoppable
{
    public static final String PPROPERTY_AGENT_PING_TIMEOUT = "pulse.agent.ping.timeout";

    private static final Logger LOG = Logger.getLogger(AgentPingService.class);

    private final int masterBuildNumber = Version.getVersion().getBuildNumberAsInt();
    private ExecutorService threadPool;
    private TokenManager serviceTokenManager;
    private Lock inProgressLock = new ReentrantLock();
    private Set<Long> inProgress = new HashSet<Long>();
    private EventManager eventManager;
    private MasterLocationProvider masterLocationProvider;

    public void init()
    {
        threadPool = new ThreadPoolExecutor(4, 30, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory()
        {
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void stop(boolean force)
    {
        if (force)
        {
            threadPool.shutdownNow();
        }
        else
        {
            threadPool.shutdown();
        }
    }

    /**
     * Requests that the given agent is pinged.  When the ping completes an
     * {@link com.zutubi.pulse.events.AgentPingEvent} is raised.
     *
     * If there is currently a ping in progress for the agent, this request
     * is ignored.  However, it is guaranteed that a ping event will be
     * raised after this method is called.
     * 
     * @param agent        the agent to ping
     * @param slaveService service that matches the given agent
     * @return true if the request was submitted, false if it was filtered
     *         due to a pending request for the same agent
     */
    public boolean requestPing(Agent agent, SlaveService slaveService)
    {
        inProgressLock.lock();
        try
        {
            // Ignore duplicate requests.  If there is ping result pending
            // for this agent, don't request another one.
            if (isPingInProgress(agent))
            {
                return false;
            }
            else
            {
                enqueueRequest(agent, slaveService);
                return true;
            }
        }
        finally
        {
            inProgressLock.unlock();
        }
    }

    private boolean isPingInProgress(Agent agent)
    {
        return inProgress.contains(agent.getId());
    }

    private void pingCompleted(Agent agent)
    {
        inProgressLock.lock();
        try
        {
            inProgress.remove(agent.getId());
        }
        finally
        {
            inProgressLock.unlock();
        }
    }

    private void enqueueRequest(final Agent agent, SlaveService slaveService)
    {
        // Directly submit the ping to the pool for execution.  Note that
        // this thread may be stuck until a network timeout.
        final Future<SlaveStatus> future = threadPool.submit(new AgentPing(agent, slaveService, masterBuildNumber, masterLocationProvider.getMasterUrl(), serviceTokenManager.getToken()));

        // Run a second thread to wait for up to the agent ping timeout for
        // the result of the ping.  This way we can send out the ping event
        // after at most the agent timeout period, even when the original
        // thread is still waiting on the network.
        threadPool.execute(new Runnable()
        {
            public void run()
            {
                SlaveStatus status;
                try
                {
                    status = future.get(Integer.getInteger(PPROPERTY_AGENT_PING_TIMEOUT, 45), TimeUnit.SECONDS);
                }
                catch (TimeoutException e)
                {
                    LOG.warning("Timed out pinging agent '" + agent.getName() + "'", e);
                    status = new SlaveStatus(Status.OFFLINE, "Agent ping timed out");
                }
                catch (Exception e)
                {
                    LOG.debug(e);

                    String message = "Unexpected error pinging agent '" + agent.getName() + "': " + e.getMessage();
                    LOG.warning(message);
                    status = new SlaveStatus(Status.OFFLINE, message);
                }

                pingCompleted(agent);
                eventManager.publish(new AgentPingEvent(this, agent, status));
            }
        });
    }

    public void setServiceTokenManager(TokenManager serviceTokenManager)
    {
        this.serviceTokenManager = serviceTokenManager;
    }

    public void setMasterLocationProvider(MasterLocationProvider masterLocationProvider)
    {
        this.masterLocationProvider = masterLocationProvider;
    }

    public void setEventManager(EventManager eventManager)
    {
        this.eventManager = eventManager;
    }
}
