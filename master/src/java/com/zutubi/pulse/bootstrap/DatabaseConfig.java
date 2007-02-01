package com.zutubi.pulse.bootstrap;

import java.util.Properties;

/**
 * The database configuration object represents all of the connection configuration details.
 *
 *
 */
public class DatabaseConfig
{
    protected static final String JDBC_DRIVER_CLASS_NAME = "jdbc.driverClassName";
    protected static final String JDBC_URL = "jdbc.url";
    protected static final String JDBC_USERNAME = "jdbc.username";
    protected static final String JDBC_PASSWORD = "jdbc.password";
    protected static final String JDBC_PROPERTY_PREFIX = "jdbc.property.";
    protected static final String HIBERNATE_PROPERTY_PREFIX = "hibernate.";

    /**
     * The internal configuration store.
     */
    private final Properties properties;

    private MasterUserPaths userPaths;

    public DatabaseConfig(Properties config)
    {
        this.properties = config;
    }

    public Properties getProperties()
    {
        return properties;
    }

    /**
     * The name of the jdbc driver class to be used to handle communications with the
     * database.
     *
     * @return the classname.
     */
    public String getDriverClassName()
    {
        return properties.getProperty(JDBC_DRIVER_CLASS_NAME);
    }

    /**
     * The JDBC connection URL.
     *
     * @return the jdbc url.
     */
    public String getUrl()
    {
        String url = properties.getProperty(JDBC_URL);
        if (url.contains("DB_ROOT") && userPaths != null)
        {
            // process the substitution iff the user paths is available.
            url = url.replace("DB_ROOT", userPaths.getDatabaseRoot().getAbsolutePath());
        }
        return url;
    }

    /**
     * The jdbc connection authorization username.
     *
     * @return username
     */
    public String getUsername()
    {
        return properties.getProperty(JDBC_USERNAME);
    }
    
    /**
     * The jdbc connection authorization password.
     *
     * @return password
     */
    public String getPassword()
    {
        return properties.getProperty(JDBC_PASSWORD);
    }

    /**
     * Retrieve the connection related properties. These are the properties with the
     * jdbc.property prefix. The returned map will contain the keys with the prefix
     * removed.
     *
     * @return a map of the connection properties.
     */
    public Properties getConnectionProperties()
    {
        Properties props = new Properties();
        for (Object o : properties.keySet())
        {
            String propertyName = (String) o;
            if (propertyName.startsWith(JDBC_PROPERTY_PREFIX))
            {
                String key = propertyName.substring(14);
                String value = properties.getProperty(propertyName);
                props.put(key, value);
            }
        }
        return props;
    }

    /**
     * Retrieve the hibernate related properties. These are the properties with the
     * hiberate prefix.
     *
     * @return a map of the hibernate properties.
     */
    public Properties getHibernateProperties()
    {
        Properties hibernateProperties = new Properties();
        for (Object o : properties.keySet())
        {
            String propertyName = (String) o;
            if (propertyName.startsWith(HIBERNATE_PROPERTY_PREFIX))
            {
                String value = properties.getProperty(propertyName);
                hibernateProperties.put(propertyName, value);
            }
        }
        return hibernateProperties;
    }

    /**
     * Set the user paths resource, used to handle substitution of variables in the
     * database configuration.
     *
     * @param userPaths instance.
     */
    public void setUserPaths(MasterUserPaths userPaths)
    {
        this.userPaths = userPaths;
    }
}
