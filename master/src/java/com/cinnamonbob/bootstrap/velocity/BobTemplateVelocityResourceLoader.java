package com.cinnamonbob.bootstrap.velocity;

import com.cinnamonbob.bootstrap.ApplicationPaths;
import com.cinnamonbob.bootstrap.ConfigUtils;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;

import com.cinnamonbob.util.logging.Logger;

/**
 * 
 *
 */
public class BobTemplateVelocityResourceLoader extends FileResourceLoader
{

    private static final Logger LOG = Logger.getLogger(BobTemplateVelocityResourceLoader.class);

    public static String getFullTemplatePath()
    {
        ApplicationPaths paths = ConfigUtils.getManager().getApplicationPaths();
        return paths.getTemplateRoot().getAbsolutePath();
    }

    public void init(ExtendedProperties configuration)
    {
        // NOTE: the path can be a comma separated list of paths... 
        configuration.setProperty("path", getFullTemplatePath());
        super.init(configuration);
    }

}
