package com.zutubi.pulse.master.build.queue;

import com.zutubi.events.DefaultEventManager;
import com.zutubi.events.EventManager;
import com.zutubi.events.RecordingEventListener;
import com.zutubi.pulse.core.BuildRevision;
import com.zutubi.pulse.core.PulseExecutionContext;
import com.zutubi.pulse.core.engine.api.ResultState;
import com.zutubi.pulse.core.scm.api.Revision;
import com.zutubi.pulse.master.build.control.BuildController;
import com.zutubi.pulse.master.build.control.BuildControllerFactory;
import com.zutubi.pulse.master.events.build.BuildCompletedEvent;
import com.zutubi.pulse.master.events.build.BuildRequestEvent;
import com.zutubi.pulse.master.events.build.PersonalBuildRequestEvent;
import com.zutubi.pulse.master.events.build.SingleBuildRequestEvent;
import com.zutubi.pulse.master.model.*;
import com.zutubi.pulse.master.scheduling.Scheduler;
import com.zutubi.pulse.master.scheduling.Trigger;
import static com.zutubi.pulse.master.tove.config.MasterConfigurationRegistry.EXTENSION_PROJECT_TRIGGERS;
import com.zutubi.pulse.master.tove.config.project.DependencyConfiguration;
import com.zutubi.pulse.master.tove.config.project.ProjectConfiguration;
import com.zutubi.pulse.master.tove.config.project.triggers.DependentBuildTriggerConfiguration;
import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Mapping;
import com.zutubi.util.Predicate;
import com.zutubi.util.bean.WiringObjectFactory;
import com.zutubi.util.junit.ZutubiTestCase;
import com.zutubi.util.reflection.ReflectionUtils;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Base test case with loads of goodies needed to test the
 * individual components of the queuing system.
 */
public abstract class BaseQueueTestCase extends ZutubiTestCase
{
    protected AtomicInteger nextId = new AtomicInteger(1);

    protected WiringObjectFactory objectFactory;

    protected Scheduler scheduler;
    private BuildManager buildManager;
    protected ProjectManager projectManager;
    protected BuildRequestRegistry buildRequestRegistry;
    protected BuildControllerFactory buildControllerFactory;
    protected EventManager eventManager;
    protected RecordingEventListener listener;
    protected Map<BuildRequestEvent, BuildController> controllers;
    protected List<ProjectConfiguration> allConfigs = new LinkedList<ProjectConfiguration>();
    protected Map<Long, Project> idToProject = new HashMap<Long, Project>();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        objectFactory = new WiringObjectFactory();
        
        buildRequestRegistry = mock(BuildRequestRegistry.class);
        buildManager = mock(BuildManager.class);
        projectManager = mock(ProjectManager.class);
        stub(projectManager.getAllProjectConfigs(anyBoolean())).toReturn(allConfigs);
        stub(projectManager.getDownstreamDependencies((ProjectConfiguration) anyObject())).toAnswer(new Answer<List<ProjectConfiguration>>()
        {
            public List<ProjectConfiguration> answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                ProjectConfiguration config = (ProjectConfiguration) invocationOnMock.getArguments()[0];
                List<ProjectConfiguration> result = new LinkedList<ProjectConfiguration>();
                for (ProjectConfiguration project : allConfigs)
                {
                    for (DependencyConfiguration dep : project.getDependencies().getDependencies())
                    {
                        if (dep.getProject().equals(config))
                        {
                            result.add(project);
                            break;
                        }
                    }
                }

                return result;
            }
        });

        stub(projectManager.mapConfigsToProjects(anyList())).toAnswer(new Answer<List<Project>>()
        {
            public List<Project> answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                Collection<ProjectConfiguration> configs = (Collection<ProjectConfiguration>) invocationOnMock.getArguments()[0];
                return CollectionUtils.map(configs, new Mapping<ProjectConfiguration, Project>()
                {
                    public Project map(ProjectConfiguration projectConfiguration)
                    {
                        return idToProject.get(projectConfiguration.getProjectId());
                    }
                });
            }
        });
        stub(projectManager.getProject(anyLong(), anyBoolean())).toAnswer(new Answer<Project>()
        {
            public Project answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                Long projectId = (Long) invocationOnMock.getArguments()[0];
                return idToProject.get(projectId);
            }
        });

        stub(projectManager.getNextBuildNumber((Project) anyObject(), eq(true))).toReturn((long) nextId.getAndIncrement());

        buildControllerFactory = mock(BuildControllerFactory.class);
        stub(buildControllerFactory.create((BuildRequestEvent)anyObject())).toAnswer(new Answer()
        {
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                // create a controller only if we have not done so pre-emptively in the test case.
                BuildRequestEvent request = (BuildRequestEvent)invocationOnMock.getArguments()[0];
                if (!controllers.containsKey(request))
                {
                    // for those cases where we do not create the request ourselves.
                    BuildController controller = mock(BuildController.class);
                    doReturn(request.getId()).when(controller).start();
                    doReturn(request.getId()).when(controller).getBuildResultId();
                    controllers.put(request, controller);
                }
                return controllers.get(request);
            }
        });

        scheduler = mock(Scheduler.class);
        stub(scheduler.getTrigger(anyLong())).toAnswer(new Answer<Trigger>()
        {
            public Trigger answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                Trigger trigger = mock(Trigger.class);
                doReturn(Boolean.TRUE).when(trigger).isActive();
                return trigger;
            }
        });

        controllers = new HashMap<BuildRequestEvent, BuildController>();

        eventManager = new DefaultEventManager();
        listener = new RecordingEventListener();
        eventManager.register(listener);

        objectFactory.initProperties(this);
    }

    protected BuildRequestEvent createRebuildRequest(String projectName)
    {
        return createRebuildRequest(createProject(projectName));
    }
    
    protected BuildRequestEvent createRebuildRequest(Project project)
    {
        BuildRequestEvent request = createRequest(project);
        request.getOptions().setRebuild(true);
        return request;
    }

    protected BuildRequestEvent createRequest(String projectName)
    {
        return createRequest(createProject(projectName));
    }

    protected BuildRequestEvent createRequest(Project project)
    {
        return createRequest(project, "source", false, new Revision(1234));
    }

    protected BuildRequestEvent createRequest(Project project, String source, boolean replaceable, boolean jumpQueueAllowed)
    {
        BuildRequestEvent request = createRequest(project, source, replaceable, new Revision(1234));
        request.getOptions().setJumpQueueAllowed(jumpQueueAllowed);
        return request;
    }

    protected BuildRequestEvent createRequest(Project project, String source, boolean replaceable, Revision revision)
    {
        BuildReason reason = new ManualTriggerBuildReason("tester");
        TriggerOptions options = new TriggerOptions(reason, source);
        options.setReplaceable(replaceable);
        BuildRevision buildRevision = new BuildRevision(revision, true);

        BuildRequestEvent request = new SingleBuildRequestEvent(this, project, buildRevision, options);

        BuildController controller = mock(BuildController.class);
        doReturn(request.getId()).when(controller).start();
        doReturn(request.getId()).when(controller).getBuildResultId();
        controllers.put(request, controller);

        return request;
    }

    protected Project createProject(String projectName, DependencyConfiguration... dependencies)
    {
        return createProject(projectName, Project.State.IDLE, dependencies);
    }

    protected Project createProject(String projectName, Project.State state, DependencyConfiguration... dependencies)
    {
        Project project = projectManager.getProject(projectName, true);
        if (project == null)
        {
            project = new Project(state);
            project.setId(nextId.getAndIncrement());
            ProjectConfiguration projectConfiguration = new ProjectConfiguration();
            projectConfiguration.setName(projectName);
            projectConfiguration.setHandle(nextId.getAndIncrement());
            projectConfiguration.setProjectId(project.getId());
            projectConfiguration.getDependencies().getDependencies().addAll(Arrays.asList(dependencies));
            HashMap<String, Object> triggers = new HashMap<String, Object>();
            triggers.put("dependent trigger", new DependentBuildTriggerConfiguration());
            projectConfiguration.addExtension(EXTENSION_PROJECT_TRIGGERS, triggers);
            project.setConfig(projectConfiguration);

            doReturn(project).when(projectManager).getProject(projectName, true);
            doReturn(project).when(projectManager).getProject(project.getId(), true);
            doReturn(project).when(projectManager).getProject(project.getId(), false);

            allConfigs.add(projectConfiguration);
            idToProject.put(project.getId(), project);
        }
        return project;
    }

    protected BuildRequestEvent createPersonalRequest(String projectName)
    {
        return createPersonalRequest(createProject(projectName));
    }

    protected BuildRequestEvent createPersonalRequest(Project project)
    {
        User user = new User();
        user.setId(nextId.getAndIncrement());
        BuildRevision revision = new BuildRevision(new Revision(1234), true);

        BuildRequestEvent request = new PersonalBuildRequestEvent(this, nextId.getAndIncrement(), revision, user, null, null, project.getConfig());

        BuildController controller = mock(BuildController.class);
        doReturn(request.getId()).when(controller).start();
        doReturn(request.getId()).when(controller).getBuildResultId();
        controllers.put(request, controller);

        return request;
    }

    protected <T> void assertItemsEqual(List<T> actual, T... expected)
    {
        assertEquals(expected.length, actual.size());

        for (final T expectedItem : expected)
        {
            assertNotNull(CollectionUtils.find(actual, new Predicate<T>()
            {
                public boolean satisfied(T actualItem)
                {
                    return expectedItem == actualItem;
                }
            }));
        }
    }

    protected QueuedRequest queue(BuildRequestEvent request)
    {
        return queue(request, new QueueThisRequest());
    }

    protected QueuedRequest queue(BuildRequestEvent request, QueuedRequestPredicate... predicates)
    {
        return new QueuedRequest(request, predicates);
    }

    protected QueuedRequest queueRequest(String projectName)
    {
        return queue(createRequest(projectName));
    }

    protected QueuedRequest active(BuildRequestEvent request)
    {
        return active(request, new ActivateThisRequest());
    }

    protected QueuedRequest active(BuildRequestEvent request, QueuedRequestPredicate... predicates)
    {
        return new QueuedRequest(request, predicates);
    }

    protected QueuedRequest activeRequest(String projectName)
    {
        return active((createRequest(projectName)));
    }

    protected BuildCompletedEvent createFailed(BuildRequestEvent request)
    {
        BuildCompletedEvent evt = createCompletedEvent(request);
        evt.getBuildResult().setState(ResultState.FAILURE);
        return evt;
    }

    protected BuildCompletedEvent createSuccessful(BuildRequestEvent request)
    {
        BuildCompletedEvent evt = createCompletedEvent(request);
        evt.getBuildResult().setState(ResultState.SUCCESS);
        return evt;
    }

    protected BuildCompletedEvent createErrored(BuildRequestEvent request)
    {
        BuildCompletedEvent evt = createCompletedEvent(request);
        evt.getBuildResult().setState(ResultState.ERROR);
        return evt;
    }

    protected BuildCompletedEvent createFailed(long metaBuildId, Project owner)
    {
        BuildCompletedEvent evt = createCompletedEvent(metaBuildId, owner);
        evt.getBuildResult().setState(ResultState.FAILURE);
        return evt;
    }

    protected BuildCompletedEvent createSuccessful(long metaBuildId, Project owner)
    {
        BuildCompletedEvent evt = createCompletedEvent(metaBuildId, owner);
        evt.getBuildResult().setState(ResultState.SUCCESS);
        return evt;
    }

    protected BuildCompletedEvent createCompletedEvent(long metaBuildId, Project owner)
    {
        // For build requests that are generated by the handlers internally, we need some way to
        // produce build completed events.  The easiest way is to create a fake one.  The important
        // details are the owner and the metabuildid.

        BuildRequestEvent fakeRequest = createRequest(owner);
        fakeRequest.setMetaBuildId(metaBuildId);
        return createCompletedEvent(fakeRequest);
    }

    private BuildCompletedEvent createCompletedEvent(BuildRequestEvent request)
    {
        BuildResult result = request.createResult(projectManager, buildManager);

        result.setMetaBuildId(request.getMetaBuildId());
        PulseExecutionContext ctx = new PulseExecutionContext();
        return new BuildCompletedEvent(this, result, ctx);
    }

    protected DependencyConfiguration dependency(Project project)
    {
        DependencyConfiguration dependencyConfiguration = new DependencyConfiguration();
        dependencyConfiguration.setProject(project.getConfig());
        return dependencyConfiguration;
    }

    protected void setProjectState(Project.State state, Project... projects)
    {
        // update the state property since the actual implemenation is stubbed.
        try
        {
            for (Project project : projects)
            {
                ReflectionUtils.setFieldValue(project, Project.class.getDeclaredField("state"), state);
            }
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
    }

    private class QueueThisRequest implements QueuedRequestPredicate
    {
        public boolean satisfied(QueuedRequest queuedRequest)
        {
            return false;
        }
    }

    private class ActivateThisRequest implements QueuedRequestPredicate
    {
        public boolean satisfied(QueuedRequest queuedRequest)
        {
            return true;
        }
    }
}