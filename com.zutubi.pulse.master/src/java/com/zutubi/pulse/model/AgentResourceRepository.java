package com.zutubi.pulse.model;

import com.zutubi.pulse.core.ResourceRepository;
import com.zutubi.pulse.core.config.Resource;
import com.zutubi.pulse.core.config.ResourceRequirement;
import com.zutubi.pulse.tove.config.agent.AgentConfiguration;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A resource repository backed by the configuration subsystem.
 */
public class AgentResourceRepository implements ResourceRepository
{
    private AgentConfiguration agentConfig;

    public AgentResourceRepository(AgentConfiguration agentConfig)
    {
        this.agentConfig = agentConfig;
    }

    public AgentConfiguration getAgentConfig()
    {
        return agentConfig;
    }

    public boolean hasResource(ResourceRequirement requirement)
    {
        String name = requirement.getResource();
        String version = requirement.getVersion();

        Resource r = getResource(name);
        if (r == null)
        {
            return false;
        }

        return requirement.isDefaultVersion() || r.getVersion(version) != null;
    }

    public boolean hasResource(String name)
    {
        return getResource(name) != null;
    }

    public Resource getResource(String name)
    {
        return agentConfig.getResources().get(name);
    }

    public List<String> getResourceNames()
    {
        return new LinkedList<String>(agentConfig.getResources().keySet());
    }

    public Map<String, Resource> getAll()
    {
        return agentConfig.getResources();
    }
}
