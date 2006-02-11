package com.cinnamonbob.model;

import org.apache.velocity.VelocityContext;

import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 */
public class AntBobFileDetails extends TemplateBobFileDetails
{
    private String buildFile;
    /**
     * Space-separated list of target names (persists more efficiently)
     */
    private String targets;
    private Map<String, String> environment;

    public AntBobFileDetails()
    {
        buildFile = null;
        targets = null;
        environment = new TreeMap<String, String>();
    }

    public AntBobFileDetails(String buildFile, String targets, Map<String, String> environment)
    {
        this.buildFile = buildFile;
        this.targets = targets;
        this.environment = environment;
    }

    protected String getTemplateName()
    {
        return "ant.template.vm";
    }

    protected void populateContext(VelocityContext context)
    {
        if (buildFile != null)
        {
            context.put("buildFile", buildFile);
        }

        if (targets != null)
        {
            context.put("targets", targets);
        }

        context.put("environment", environment);
    }

    public String getBuildFile()
    {
        return buildFile;
    }

    public void setBuildFile(String buildFile)
    {
        this.buildFile = buildFile;
    }

    public String getTargets()
    {
        return targets;
    }

    public void setTargets(String targets)
    {
        this.targets = targets;
    }

    public Map<String, String> getEnvironment()
    {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment)
    {
        this.environment = environment;
    }

    public void addEnvironmentalVariable(String name, String value)
    {
        environment.put(name, value);
    }

    public String getType()
    {
        return "jakarta ant";
    }

    public Properties getProperties()
    {
        // TODO i18n
        Properties result = new Properties();

        if (buildFile != null)
        {
            result.put("build file", buildFile);
        }

        if (targets != null)
        {
            result.put("targets", targets);
        }

        String env = getEnvironmentString();
        if (env.length() > 0)
        {
            result.put("environment", env);
        }

        return result;
    }

    private String getEnvironmentString()
    {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry entry : environment.entrySet())
        {
            if (!first)
            {
                result.append("; ");
                first = false;
            }

            result.append(entry.getKey());
            result.append('=');
            result.append(entry.getValue());
        }

        return result.toString();
    }
}
