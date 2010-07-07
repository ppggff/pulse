package com.zutubi.pulse.master;

import com.zutubi.events.DefaultEventManager;
import com.zutubi.events.EventManager;
import com.zutubi.pulse.core.BuildRevision;
import com.zutubi.pulse.core.engine.PulseFileSource;
import com.zutubi.pulse.core.model.NamedEntity;
import com.zutubi.pulse.core.scm.api.Revision;
import com.zutubi.pulse.core.scm.config.MockScmConfiguration;
import com.zutubi.pulse.core.test.api.PulseTestCase;
import com.zutubi.pulse.master.agent.MasterLocationProvider;
import com.zutubi.pulse.master.bootstrap.MasterConfigurationManager;
import com.zutubi.pulse.master.events.build.AbstractBuildRequestEvent;
import com.zutubi.pulse.master.model.*;
import com.zutubi.pulse.master.security.PulseThreadFactory;
import com.zutubi.pulse.master.tove.config.project.ProjectConfiguration;
import com.zutubi.pulse.master.tove.config.project.ResourcePropertyConfiguration;
import com.zutubi.pulse.master.tove.config.project.hooks.BuildHookManager;
import com.zutubi.pulse.servercore.bootstrap.MasterUserPaths;
import com.zutubi.tove.security.AccessManager;
import com.zutubi.util.FileSystemUtils;
import com.zutubi.util.bean.WiringObjectFactory;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyLong;
import org.mockito.Mockito;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base helper test class for tests of the build queues, which share many
 * dependencies.
 */
public class BuildQueueTestCase extends PulseTestCase
{
    protected MasterConfigurationManager configurationManager;
    protected EventManager eventManager;
    protected ProjectManager projectManager;
    protected BuildManager buildManager;
    protected ThreadFactory threadFactory;
    protected WiringObjectFactory objectFactory;
    protected MasterLocationProvider masterLocationProvider;
    protected BuildHookManager buildHookManager;
    protected TestManager testManager;
    protected File tempDir;
    protected AtomicInteger nextId = new AtomicInteger(1);
    protected AccessManager accessManager;
    protected BuildRequestRegistry buildRequestRegistry;

    protected void setUp() throws Exception
    {
        super.setUp();

        configurationManager = mock(MasterConfigurationManager.class);
        doReturn(mock(MasterUserPaths.class)).when(configurationManager).getUserPaths();
        tempDir = FileSystemUtils.createTempDir(getName(), ".tmp");
        doReturn(tempDir).when(configurationManager).getDataDirectory();

        projectManager = mock(ProjectManager.class);

        masterLocationProvider = mock(MasterLocationProvider.class);
        doReturn("mock location").when(masterLocationProvider).getMasterLocation();
        doReturn("mock url").when(masterLocationProvider).getMasterUrl();

        buildHookManager = mock(BuildHookManager.class);
        eventManager = new DefaultEventManager();
        buildManager = mock(BuildManager.class);
        threadFactory = new PulseThreadFactory();
        objectFactory = new WiringObjectFactory();
        testManager = mock(TestManager.class);
        accessManager = mock(AccessManager.class);

        buildRequestRegistry = new BuildRequestRegistry();
        buildRequestRegistry.setAccessManager(accessManager);
        ProjectManager projectManager = mock(ProjectManager.class);
        Mockito.stub(projectManager.getProject(anyLong(), anyBoolean())).toReturn(new Project());
        buildRequestRegistry.setProjectManager(projectManager);
    }

    protected void tearDown() throws Exception
    {
        FileSystemUtils.rmdir(tempDir);
        super.tearDown();
    }

    protected void assertActive(List<EntityBuildQueue.ActiveBuild> activeSnapshot, AbstractBuildRequestEvent... events)
    {
        if (events.length == 0)
        {
            if (activeSnapshot != null)
            {
                assertEquals(0, activeSnapshot.size());
            }
        }
        else
        {
            assertEquals(events.length, activeSnapshot.size());
            for (int i = 0; i < events.length; i++)
            {
                AbstractBuildRequestEvent event = events[i];
                assertSame(event, activeSnapshot.get(i).getEvent());
                assertEquals(BuildRequestRegistry.RequestStatus.ACTIVATED, buildRequestRegistry.getStatus(event.getId()));
            }
        }
    }

    protected void assertQueued(List<AbstractBuildRequestEvent> queuedSnapshot, AbstractBuildRequestEvent... events)
    {
        if (events.length == 0)
        {
            if (queuedSnapshot != null)
            {
                assertEquals(0, queuedSnapshot.size());
            }
        }
        else
        {
            assertEquals(events.length, queuedSnapshot.size());
            for (int i = 0; i < events.length; i++)
            {
                AbstractBuildRequestEvent event = events[i];
                assertSame(event, queuedSnapshot.get(i));
                assertEquals(BuildRequestRegistry.RequestStatus.QUEUED, buildRequestRegistry.getStatus(event.getId()));
            }
        }
    }

    protected void assertRejected(AbstractBuildRequestEvent... events)
    {
        assertStatus(BuildRequestRegistry.RequestStatus.REJECTED, events);
    }

    protected void assertAssimilated(AbstractBuildRequestEvent... events)
    {
        assertStatus(BuildRequestRegistry.RequestStatus.ASSIMILATED, events);
    }

    protected void assertCancelled(AbstractBuildRequestEvent... events)
    {
        assertStatus(BuildRequestRegistry.RequestStatus.CANCELLED, events);
    }

    private void assertStatus(BuildRequestRegistry.RequestStatus expectedStatus, AbstractBuildRequestEvent... events)
    {
        for (AbstractBuildRequestEvent event: events)
        {
            assertEquals(expectedStatus, buildRequestRegistry.getStatus(event.getId()));
        }
    }

    protected Project createProject()
    {
        Project project = new Project();
        project.setId(nextId.getAndIncrement());
        ProjectConfiguration projectConfiguration = new ProjectConfiguration();
        projectConfiguration.setScm(new MockScmConfiguration());
        projectConfiguration.setProjectId(project.getId());
        project.setConfig(projectConfiguration);
        return project;
    }

    protected AbstractBuildRequestEvent createRequest(final Project owner, final long buildId, String source, boolean replaceable, Revision revision)
    {
        BuildRevision buildRevision = revision == null ? new BuildRevision() : new BuildRevision(revision, new PulseFileSource("pulse file"), false);
        AbstractBuildRequestEvent requestEvent = new AbstractBuildRequestEvent(BuildQueueTestCase.this, buildRevision, owner.getConfig(), Collections.<ResourcePropertyConfiguration>emptyList(), null, source, replaceable)
        {
            public NamedEntity getOwner()
            {
                return owner;
            }

            public boolean isPersonal()
            {
                return false;
            }

            public BuildResult createResult(ProjectManager projectManager, UserManager userManager)
            {
                BuildResult buildResult = new BuildResult(new UnknownBuildReason(), owner, 0, false);
                buildResult.setId(buildId);
                return buildResult;
            }
        };
        buildRequestRegistry.register(requestEvent);
        return requestEvent;
    }
}