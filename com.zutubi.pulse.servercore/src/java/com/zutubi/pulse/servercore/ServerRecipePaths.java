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

package com.zutubi.pulse.servercore;

import com.zutubi.pulse.core.RecipePaths;
import com.zutubi.pulse.core.engine.api.BuildProperties;
import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.tove.variables.SimpleVariable;
import com.zutubi.tove.variables.VariableResolver;
import com.zutubi.tove.variables.api.MutableVariableMap;
import com.zutubi.tove.variables.api.ResolutionException;
import com.zutubi.util.io.FileSystemUtils;
import com.zutubi.util.logging.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.zutubi.pulse.core.engine.api.BuildProperties.NAMESPACE_INTERNAL;
import static com.zutubi.pulse.core.engine.api.BuildProperties.PROPERTY_SKIP_CHECKOUT;
import static com.zutubi.tove.variables.VariableResolver.ResolutionStrategy.RESOLVE_STRICT;

/**
 * The server recipe paths, which by default live at:
 * <ul>
 *   <li>$(data.dir)/agents/$(agent.handle)/recipes/$(recipe.id) - for transient recipes, and</li>
 *   <li>$(data.dir)/agents/$(agent.handle)/work/$(project.handle)/$(stage.handle) - for persistent recipes.</li>
 * </ul>
 */
public class ServerRecipePaths implements RecipePaths
{
    private static final Logger LOG = Logger.getLogger(ServerRecipePaths.class);

    public static final String PROPERTY_DATA_DIR = "data.dir";
    public static final String PROPERTY_AGENT_DATA_DIR = "agent.data.dir";
    public static final String PROPERTY_RECIPES_DIR = "recipes.dir";
    
    private AgentRecipeDetails recipeDetails;
    private File dataDir;
    private boolean skipCheckout = false;

    public ServerRecipePaths(AgentRecipeDetails recipeDetails, File dataDir)
    {
        this.recipeDetails = recipeDetails;
        this.dataDir = dataDir;
    }

    public ServerRecipePaths(ExecutionContext context, File dataDir)
    {
        this(new AgentRecipeDetails(context), dataDir);
        skipCheckout = context.getBoolean(NAMESPACE_INTERNAL, PROPERTY_SKIP_CHECKOUT, false);
    }

    private File getAgentDataDir()
    {
        MutableVariableMap references = recipeDetails.createPathVariableMap();
        references.add(new SimpleVariable<String>(PROPERTY_DATA_DIR, dataDir.getAbsolutePath()));

        try
        {
            String path = VariableResolver.resolveVariables(recipeDetails.getAgentDataPattern(), references, RESOLVE_STRICT);
            return new File(path);
        }
        catch (ResolutionException e)
        {
            LOG.warning("Invalid agent data directory '" + recipeDetails.getAgentDataPattern() + "': " + e.getMessage(), e);
            return new File(dataDir, FileSystemUtils.composeFilename("agents", Long.toString(recipeDetails.getAgentHandle())));
        }
    }

    public File getRecipesRoot()
    {
        return new File(getAgentDataDir(), "recipes");
    }

    public File getRecipeRoot()
    {
        return new File(getRecipesRoot(), Long.toString(recipeDetails.getRecipeId()));
    }

    private File resolveDirPattern(String pattern, File defaultDir)
    {
        MutableVariableMap references = recipeDetails.createPathVariableMap();
        references.add(new SimpleVariable<String>(PROPERTY_AGENT_DATA_DIR, getAgentDataDir().getAbsolutePath()));
        references.add(new SimpleVariable<String>(PROPERTY_DATA_DIR, dataDir.getAbsolutePath()));
        references.add(new SimpleVariable<String>(PROPERTY_RECIPES_DIR, getRecipesRoot().getAbsolutePath()));

        try
        {
            String path = VariableResolver.resolveVariables(pattern, references, RESOLVE_STRICT);
            return new File(path);
        }
        catch (ResolutionException e)
        {
            LOG.warning("Invalid directory pattern '" + pattern + "': " + e.getMessage() + "; using default '" + defaultDir.getAbsolutePath() + "'", e);
            return defaultDir;
        }
    }

    public File getPersistentWorkDir()
    {
        File defaultDir = new File(getAgentDataDir(), FileSystemUtils.composeFilename("work", Long.toString(recipeDetails.getProjectHandle()), Long.toString(recipeDetails.getStageHandle())));
        return resolveDirPattern(recipeDetails.getProjectPersistentPattern(), defaultDir);
    }

    public File getTempDir()
    {
        return resolveDirPattern(recipeDetails.getProjectTempPattern(), new File(getRecipeRoot(), "base"));
    }

    public File getCheckoutDir()
    {
        if (skipCheckout)
        {
            return null;
        }
        else if (recipeDetails.isUpdate())
        {
            return getPersistentWorkDir();
        }
        else
        {
            return getTempDir();
        }
    }

    public File getBaseDir()
    {
        if (recipeDetails.isIncremental())
        {
            return getPersistentWorkDir();
        }
        else
        {
            return getTempDir();
        }
    }

    public File getOutputDir()
    {
        return new File(getRecipeRoot(), "output");
    }

    public Map<String, String> getPathProperties()
    {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(BuildProperties.PROPERTY_BASE_DIR, getBaseDir().getAbsolutePath());
        properties.put(BuildProperties.PROPERTY_DATA_DIR, dataDir.getAbsolutePath());
        properties.put(BuildProperties.PROPERTY_AGENT_DATA_DIR, getAgentDataDir().getAbsolutePath());
        properties.put(BuildProperties.PROPERTY_OUTPUT_DIR, getOutputDir().getAbsolutePath());
        return properties;
    }
}
