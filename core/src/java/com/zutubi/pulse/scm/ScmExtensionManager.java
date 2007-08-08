package com.zutubi.pulse.scm;

import com.zutubi.pulse.plugins.AbstractExtensionManager;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;

/**
 * Extension manager for managing scm implementations
 * 
 */
public class ScmExtensionManager extends AbstractExtensionManager
{
    private DelegateScmClientFactory clientFactory;

    protected String getExtensionPointId()
    {
        return "com.zutubi.pulse.core.scms";
    }

    protected void handleConfigurationElement(IExtension extension, IExtensionTracker tracker, IConfigurationElement config)
    {
        // check configuration.

        try
        {
            String name = config.getAttribute("name");
            String configClass = config.getAttribute("config-class");
            String clientClass = config.getAttribute("factory-class");
            System.out.println(String.format("Adding SCM: %s -> (%s, %s)", name, configClass, clientClass));

            Class configClazz = loadClass(extension, configClass);
            Class clientClazz = loadClass(extension, clientClass);
            if(configClazz != null && clientClazz != null)
            {
                tracker.registerObject(extension, name, IExtensionTracker.REF_WEAK);
            }

            clientFactory.register(configClazz, clientClazz);
        }
        catch (ScmException e)
        {
            e.printStackTrace();
        }
    }

    public void removeExtension(IExtension extension, Object[] objects)
    {
        for (Object o : objects)
        {
        }
    }

    public void setScmClientFactory(DelegateScmClientFactory clientFactory)
    {
        this.clientFactory = clientFactory;
    }
}
