package com.zutubi.prototype;

import com.zutubi.prototype.type.Type;
import com.zutubi.pulse.core.config.Configuration;

/**
 *
 *
 */
public class ConventionSupport
{
    public static Class getWizard(Type type)
    {
        return loadClass(type, "Wizard");
    }

    public static Class getActions(Class<? extends Configuration> clazz)
    {
        return loadClass(clazz, "Actions");
    }

    public static Class getCleanupTasks(Class<? extends Configuration> clazz)
    {
        return loadClass(clazz, "CleanupTasks");
    }

    public static Class getFormatter(Type type)
    {
        return loadClass(type, "Formatter");
    }

    public static Class getDisplay(Type type)
    {
        return loadClass(type, "Display");
    }

    public static Class getCreator(Type type)
    {
        return loadClass(type, "Creator");
    }

    private static Class loadClass(Type type, String suffix)
    {
        // we need to search up the inheritence hierarchy.
        return loadClass(type.getClazz(), suffix);
    }

    private static Class loadClass(Class clazz, String suffix)
    {
        if(clazz.isArray() || clazz.isPrimitive())
        {
            return null;
        }
        
        while (clazz != Object.class)
        {
            try
            {
                String className = clazz.getName() + suffix;
                return clazz.getClassLoader().loadClass(className);
            }
            catch (ClassNotFoundException e)
            {
                // noops.
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
