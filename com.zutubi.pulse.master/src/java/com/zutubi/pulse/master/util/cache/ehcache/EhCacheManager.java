/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.master.util.cache.ehcache;

import com.google.common.io.Resources;
import com.zutubi.pulse.servercore.bootstrap.SystemPaths;
import com.zutubi.util.io.IOUtils;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import org.hsqldb.lib.StringInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Handles the creation and configuration of the underlying EhCache manager,
 * and provides a bridge between ehcache implementations and Pulse cache 
 * interfaces.
 */
public class EhCacheManager implements com.zutubi.pulse.master.util.cache.CacheManager
{
    private static final String CONFIG_FILE = "/ehcache-template.xml";
    private static final String TEMP_DIR_TOKEN = "pulse.system.temp.dir";

    private CacheManager cacheManager;
    private SystemPaths systemPaths;

    public void init() throws IOException
    {
        String configContent = Resources.asCharSource(getClass().getResource(CONFIG_FILE), Charset.defaultCharset()).read();
        configContent = configContent.replace(TEMP_DIR_TOKEN, systemPaths.getTmpRoot().getAbsolutePath());

        InputStream stream = null;
        try
        {
            stream = new StringInputStream(configContent);
            cacheManager = CacheManager.create(stream);
        }
        catch (CacheException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            IOUtils.close(stream);
        }
    }

    public synchronized EhCache getCache(String name)
    {
        ensureCacheExists(name);
        
        return new EhCache(cacheManager.getCache(name));
    }

    /**
     * Get the raw ehcache from the underlying ehcache manager.
     *
     * @param name  the name of the cache being retrieved.
     *
     * @return the underlying ehcache instance.
     */
    public Cache getEhCache(String name)
    {
        ensureCacheExists(name);

        return cacheManager.getCache(name);
    }

    private void ensureCacheExists(String name)
    {
        if (!cacheManager.cacheExists(name))
        {
            try
            {
                cacheManager.addCache(name);
            }
            catch (CacheException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public void flushCaches()
    {
        for (int i = 0; i < cacheManager.getCacheNames().length; i++)
        {
            String cacheName = cacheManager.getCacheNames()[i];
            getCache(cacheName).removeAll();
        }
    }

    public void setSystemPaths(SystemPaths systemPaths)
    {
        this.systemPaths = systemPaths;
    }
}
