package com.zutubi.pulse.slave;

import com.opensymphony.util.TextUtils;
import com.zutubi.pulse.bootstrap.SystemConfiguration;
import com.zutubi.pulse.bootstrap.SystemPaths;
import com.zutubi.pulse.bootstrap.conf.EnvConfig;
import com.zutubi.pulse.bootstrap.conf.VolatileReadOnlyConfig;
import com.zutubi.pulse.config.CompositeConfig;
import com.zutubi.pulse.config.Config;
import com.zutubi.pulse.config.ConfigSupport;
import com.zutubi.pulse.config.FileConfig;
import com.zutubi.pulse.util.FileSystemUtils;

import java.io.File;

/**
 */
public class DefaultSlaveConfiguration implements SlaveConfiguration, SystemConfiguration
{
    private static final String PROPERTIES_FILE = "pulse-agent.properties";
    private static final String CONFIG_DIR = ".pulse-agent";
    private static final String LOGGING_CONFIG = "log.config";
    private static final String LOG_EVENTS = "log.events";

    private EnvConfig envConfig;
    private ConfigSupport config;
    private SystemPaths systemPaths;

    public DefaultSlaveConfiguration(SystemPaths systemPaths, EnvConfig env)
    {
        this.systemPaths = systemPaths;
        init(env);
    }

    public void init(EnvConfig env)
    {
        this.envConfig = env;

        Config commandLineAndSystemProperties = new VolatileReadOnlyConfig(System.getProperties());

        File systemConfigProperties = new File(systemPaths.getConfigRoot(), PROPERTIES_FILE);
        FileConfig systemConfig = new FileConfig(systemConfigProperties);

        String pulseConfigProperties = env.getDefaultPulseConfig(CONFIG_DIR);
        if (env.hasPulseConfig())
        {
            pulseConfigProperties = env.getPulseConfig();
        }

        File pulseConfigFile = new File(pulseConfigProperties);
        if(pulseConfigFile.isFile())
        {
            FileConfig userConfig = new FileConfig(pulseConfigFile);
            config = new ConfigSupport(new CompositeConfig(commandLineAndSystemProperties, userConfig, systemConfig));
        }
        else
        {
            config = new ConfigSupport(new CompositeConfig(commandLineAndSystemProperties, systemConfig));
        }
    }

    public String getBindAddress()
    {
        String result = config.getProperty(WEBAPP_BIND_ADDRESS);
        if(TextUtils.stringSet(result))
        {
            return result;
        }
        else
        {
            return "0.0.0.0";
        }
    }

    public int getServerPort()
    {
        return config.getInteger(WEBAPP_PORT, 8090);
    }

    public String getLoggingLevel()
    {
        return config.getProperty(LOGGING_CONFIG, "default");
    }

    public String getContextPath()
    {
        return config.getProperty(CONTEXT_PATH, "/");
    }

    public String getContextPathNormalised()
    {
        // TODO share with master.  more of this whole class, preferrably
        String contextPath = getContextPath();
        if(!contextPath.startsWith("/"))
        {
            contextPath = "/" + contextPath;
        }
        if(contextPath.endsWith("/"))
        {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }
        return contextPath;
    }

    public void setDataPath(String path)
    {
        config.setProperty(PULSE_DATA, path);
    }

    public String getDataPath()
    {
        String defaultData = FileSystemUtils.composeFilename(envConfig.getDefaultPulseConfigDir(CONFIG_DIR), "data");
        return config.getProperty(PULSE_DATA, defaultData);
    }

    public boolean isEventLoggingEnabled()
    {
        return config.getBooleanProperty(LOG_EVENTS, false);
    }
}
