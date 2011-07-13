package com.zutubi.pulse.core;

import com.zutubi.events.EventManager;
import com.zutubi.pulse.core.commands.ArtifactFactory;
import com.zutubi.pulse.core.commands.CommandFactory;
import com.zutubi.pulse.core.commands.DefaultCommandContext;
import com.zutubi.pulse.core.commands.api.*;
import com.zutubi.pulse.core.dependency.ivy.IvyClient;
import com.zutubi.pulse.core.dependency.ivy.IvyManager;
import com.zutubi.pulse.core.dependency.ivy.IvyMessageOutputStreamAdapter;
import com.zutubi.pulse.core.dependency.ivy.RetrieveDependenciesCommandConfiguration;
import com.zutubi.pulse.core.engine.ProjectRecipesConfiguration;
import com.zutubi.pulse.core.engine.PulseFileProvider;
import com.zutubi.pulse.core.engine.RecipeConfiguration;
import com.zutubi.pulse.core.engine.api.*;
import com.zutubi.pulse.core.engine.marshal.PulseFileLoader;
import com.zutubi.pulse.core.engine.marshal.PulseFileLoaderFactory;
import com.zutubi.pulse.core.events.*;
import com.zutubi.pulse.core.marshal.FileResolver;
import com.zutubi.pulse.core.marshal.LocalFileResolver;
import com.zutubi.pulse.core.marshal.RelativeFileResolver;
import com.zutubi.pulse.core.model.*;
import com.zutubi.pulse.core.postprocessors.api.PostProcessorFactory;
import com.zutubi.pulse.core.util.PulseZipUtils;
import com.zutubi.util.FileSystemUtils;
import com.zutubi.util.Pair;
import com.zutubi.util.StringUtils;
import com.zutubi.util.logging.Logger;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.zutubi.pulse.core.RecipeUtils.addResourceProperties;
import static com.zutubi.pulse.core.engine.api.BuildProperties.*;

/**
 * The recipe processor, as the name suggests, is responsible for running recipes.
 */
public class RecipeProcessor
{
    private static final Logger LOG = Logger.getLogger(RecipeProcessor.class);

    public static final String BUILD_FIELDS_FILE = "build." + ResultCustomFields.CUSTOM_FIELDS_FILE;
    public static final String PULSE_FILE = "pulse.xml";

    private static final String LABEL_EXECUTE = "execute";

    private EventManager eventManager;
    private final Lock runningLock = new ReentrantLock();
    private long runningRecipe = 0;
    private Command runningCommand;
    private boolean terminating;
    private PulseFileLoaderFactory fileLoaderFactory;
    private CommandFactory commandFactory;
    private ArtifactFactory artifactFactory;
    private PostProcessorFactory postProcessorFactory;
    private IvyManager ivyManager;

    public void build(RecipeRequest request)
    {
        // This result holds only the recipe details (stamps, state etc), not
        // the command results.  A full recipe result with command results is
        // assembled elsewhere.
        RecipeResult recipeResult = new RecipeResult(request.getRecipeName());
        recipeResult.setId(request.getId());
        recipeResult.commence();

        PersistentTestSuiteResult testResults = new PersistentTestSuiteResult();
        Map<Pair<FieldScope, String>, String> customFields = new HashMap<Pair<FieldScope, String>, String>();

        runningRecipe = recipeResult.getId();
        eventManager.publish(new RecipeCommencedEvent(this, recipeResult.getId(), recipeResult.getRecipeName(), recipeResult.getStartTime()));

        PulseExecutionContext context = request.getContext();
        long recipeStartTime = recipeResult.getStartTime();
        pushRecipeContext(context, request, testResults, customFields, recipeStartTime);
        try
        {
            executeRequest(request);
        }
        catch (BuildException e)
        {
            recipeResult.error(e);
        }
        catch (Exception e)
        {
            LOG.severe(e);
            recipeResult.error("Unexpected error: " + e.getMessage());
        }
        finally
        {
            RecipePaths paths = context.getValue(NAMESPACE_INTERNAL, PROPERTY_RECIPE_PATHS, RecipePaths.class);
            storeTestResults(paths, recipeResult, testResults);
            storeCustomFields(paths, customFields);
            
            boolean compress = context.getBoolean(NAMESPACE_INTERNAL, PROPERTY_COMPRESS_ARTIFACTS, false);
            if (compress)
            {
                compressResults(paths);
            }

            recipeResult.complete();
            eventManager.publish(new RecipeCompletedEvent(this, recipeResult));

            context.pop();

            runningLock.lock();
            runningRecipe = 0;
            if (terminating)
            {
                terminating = false;
            }
            runningLock.unlock();
        }
    }

    private void executeRequest(RecipeRequest request) throws Exception
    {
        PulseExecutionContext context = request.getContext();
        File outputDir = context.getValue(NAMESPACE_INTERNAL, PROPERTY_RECIPE_PATHS, RecipePaths.class).getOutputDir();

        RecipeStatus status = new RecipeStatus();
        BootstrapCommandConfiguration bootstrapConfig = new BootstrapCommandConfiguration(request.getBootstrapper());
        if (pushContextAndExecute(context, bootstrapConfig, null, outputDir, status) || !status.isSuccess())
        {
            return;
        }

        ModuleDescriptor descriptor = context.getValue(NAMESPACE_INTERNAL, PROPERTY_DEPENDENCY_DESCRIPTOR, ModuleDescriptor.class);
        if (descriptor != null)
        {
            if (descriptor.getDependencies().length > 0)
            {
                IvyClient ivy = ivyManager.createIvyClient(context.getString(PROPERTY_MASTER_URL) + "/repository");
                ivy.pushMessageLogger(new IvyMessageOutputStreamAdapter(context.getOutputStream()));

                RetrieveDependenciesCommandConfiguration retrieveCommandConfig = new RetrieveDependenciesCommandConfiguration();
                retrieveCommandConfig.setIvy(ivy);
                if (pushContextAndExecute(context, retrieveCommandConfig, null, outputDir, status) || !status.isSuccess())
                {
                    return;
                }
            }
        }

        // Now we can load the recipe from the pulse file
        ProjectRecipesConfiguration recipesConfiguration = new ProjectRecipesConfiguration();
        RecipeLoadInterceptor recipeLoadPredicate = new RecipeLoadInterceptor(recipesConfiguration, request.getRecipeName());
        loadPulseFile(request, recipesConfiguration, recipeLoadPredicate, context);
        String recipeName = request.getRecipeName();
        if (!StringUtils.stringSet(recipeName))
        {
            recipeName = recipesConfiguration.getDefaultRecipe();
            if (!StringUtils.stringSet(recipeName))
            {
                throw new BuildException("Please specify a default recipe for your project.");
            }
        }

        RecipeConfiguration recipeConfiguration = recipesConfiguration.getRecipes().get(recipeName);
        if (recipeConfiguration == null)
        {
            throw new BuildException("Undefined recipe '" + recipeName + "'");
        }

        executeRecipe(recipeConfiguration, recipeLoadPredicate, status, context, outputDir);
    }

    private void compressResults(RecipePaths paths)
    {
        eventManager.publish(new RecipeStatusEvent(this, runningRecipe, "Compressing recipe artifacts..."));
        if (zipDir(paths.getOutputDir()))
        {
            eventManager.publish(new RecipeStatusEvent(this, runningRecipe, "Artifacts compressed."));
        }
    }

    private boolean zipDir(File dir)
    {
        try
        {
            File zipFile = new File(dir.getAbsolutePath() + ".zip");
            PulseZipUtils.createZip(zipFile, dir, null);
            return true;
        }
        catch (IOException e)
        {
            LOG.severe(e);
            eventManager.publish(new RecipeStatusEvent(this, runningRecipe, "Compression failed: " + e.getMessage() + "."));
            return false;
        }
    }

    private void storeTestResults(RecipePaths paths, RecipeResult recipeResult, PersistentTestSuiteResult testResults)
    {
        eventManager.publish(new RecipeStatusEvent(this, runningRecipe, "Storing test results..."));
        try
        {
            TestSuitePersister persister = new TestSuitePersister();
            File testDir = new File(paths.getOutputDir(), RecipeResult.TEST_DIR);
            FileSystemUtils.createDirectory(testDir);
            persister.write(testResults, testDir);
        }
        catch (IOException e)
        {
            LOG.severe("Unable to write out test results", e);
            recipeResult.error("Unable to write out test results: " + e.getMessage());
        }
        recipeResult.setTestSummary(testResults.getSummary());
        eventManager.publish(new RecipeStatusEvent(this, runningRecipe, "Test results stored."));
    }

    private void storeCustomFields(RecipePaths paths, Map<Pair<FieldScope, String>, String> customFields)
    {
        if (customFields.size() > 0)
        {
            eventManager.publish(new RecipeStatusEvent(this, runningRecipe, "Storing custom fields..."));

            Map<String, String> buildFields = new HashMap<String, String>();
            Map<String, String> recipeFields = new HashMap<String, String>();
            for (Map.Entry<Pair<FieldScope, String>, String> entry: customFields.entrySet())
            {
                if (entry.getKey().first == FieldScope.BUILD)
                {
                    buildFields.put(entry.getKey().second, entry.getValue());
                }
                else
                {
                    recipeFields.put(entry.getKey().second, entry.getValue());
                }
            }

            if (buildFields.size() > 0)
            {
                ResultCustomFields resultCustomFields = new ResultCustomFields(paths.getOutputDir(), BUILD_FIELDS_FILE);
                resultCustomFields.store(buildFields);
            }

            if (recipeFields.size() > 0)
            {
                ResultCustomFields resultCustomFields = new ResultCustomFields(paths.getOutputDir());
                resultCustomFields.store(recipeFields);
            }

            eventManager.publish(new RecipeStatusEvent(this, runningRecipe, "Custom fields stored."));
        }
    }

    private void pushRecipeContext(PulseExecutionContext context, RecipeRequest request, PersistentTestSuiteResult testResults, Map<Pair<FieldScope, String>, String> customFields, long recipeStartTime)
    {
        context.push();
        context.addString(NAMESPACE_INTERNAL, PROPERTY_BASE_DIR, context.getWorkingDir().getAbsolutePath());
        context.addString(NAMESPACE_INTERNAL, PROPERTY_RECIPE_TIMESTAMP, new SimpleDateFormat(TIMESTAMP_FORMAT_STRING).format(new Date(recipeStartTime)));
        context.addString(NAMESPACE_INTERNAL, PROPERTY_RECIPE_TIMESTAMP_MILLIS, Long.toString(recipeStartTime));
        context.addValue(NAMESPACE_INTERNAL, PROPERTY_TEST_RESULTS, testResults);
        context.addValue(NAMESPACE_INTERNAL, PROPERTY_CUSTOM_FIELDS, customFields);

        if (context.getString(NAMESPACE_INTERNAL, PROPERTY_RECIPE) == null)
        {
            context.addString(PROPERTY_RECIPE, "[default]");
        }

        PulseScope scope = context.getScope();
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> var : env.entrySet())
        {
            scope.addEnvironmentProperty(var.getKey(), var.getValue());
        }

        addResourceProperties(context, request.getResourceRequirements(), context.getValue(PROPERTY_RESOURCE_REPOSITORY, ResourceRepository.class));
        for (ResourceProperty property : request.getProperties())
        {
            context.add(property);
        }
    }

    private void loadPulseFile(RecipeRequest request, ProjectRecipesConfiguration recipesConfiguration, RecipeLoadInterceptor recipeLoadPredicate, PulseExecutionContext context) throws BuildException
    {
        context.setLabel(SCOPE_RECIPE);
        PulseScope globalScope = new PulseScope(context.getScope());

        PulseFileProvider pulseFileProvider = request.getPulseFileSource();
        File importRoot = pulseFileProvider.getImportRoot();
        if (importRoot == null)
        {
            importRoot = context.getWorkingDir();
        }

        LocalFileResolver localResolver = new LocalFileResolver(importRoot);
        try
        {
            // CIB-286: special case empty file for better reporting
            String pulseFileContent = pulseFileProvider.getFileContent(localResolver);
            storePulseFile(pulseFileContent, context);
            if (!StringUtils.stringSet(pulseFileContent))
            {
                throw new BuildException("Unable to parse pulse file: File is empty");
            }

            // load the pulse file from the source.
            PulseFileLoader fileLoader = fileLoaderFactory.createLoader();
            FileResolver relativeResolver = new RelativeFileResolver(pulseFileProvider.getPath(), localResolver);
            fileLoader.loadRecipe(pulseFileContent, recipesConfiguration, recipeLoadPredicate, globalScope, relativeResolver);
        }
        catch (Exception e)
        {
            throw new BuildException("Unable to parse pulse file: " + e.getMessage(), e);
        }
    }

    private void storePulseFile(String pulseFileContent, PulseExecutionContext context) throws IOException
    {
        RecipePaths paths = context.getValue(PROPERTY_RECIPE_PATHS, RecipePaths.class);
        FileSystemUtils.createFile(new File(paths.getOutputDir(), PULSE_FILE), pulseFileContent);
    }

    public void executeRecipe(RecipeConfiguration config, RecipeLoadInterceptor loadInterceptor, RecipeStatus status, PulseExecutionContext context, File outputDir)
    {
        context.push();
        try
        {
            for (CommandConfiguration commandConfig : config.getCommands().values())
            {
                if (status.isSuccess() || commandConfig.isForce())
                {
                    boolean recipeTerminated = pushContextAndExecute(context, commandConfig, loadInterceptor.getCommandScope(commandConfig.getName()), outputDir, status);
                    if (recipeTerminated)
                    {
                        return;
                    }
                }
            }
        }
        finally
        {
            context.pop();
        }
    }

    private String getCommandDirName(int i, CommandResult result)
    {
        // Use the command name because:
        // a) we do not have an id for the command model
        // b) for local builds, this is a lot friendlier for the developer
        return String.format("%08d-%s", i, FileSystemUtils.encodeFilenameComponent(result.getCommandName()));
    }

    private boolean pushContextAndExecute(PulseExecutionContext context, CommandConfiguration commandConfig, Scope scope, File outputDir, RecipeStatus status)
    {
        CommandResult result = new CommandResult(commandConfig.getName());
        File commandOutput = new File(outputDir, getCommandDirName(status.nextCommandIndex(), result));
        if (!commandOutput.mkdirs())
        {
            throw new BuildException("Could not create command output directory '" + commandOutput.getAbsolutePath() + "'");
        }
        result.setOutputDir(commandOutput.getPath());

        context.setLabel(LABEL_EXECUTE);
        context.push();
        context.addString(NAMESPACE_INTERNAL, PROPERTY_OUTPUT_DIR, commandOutput.getAbsolutePath());
        context.addString(NAMESPACE_INTERNAL, PROPERTY_RECIPE_STATUS, status.getState().getString());

        if (scope != null)
        {
            context.getScope().add((PulseScope) scope);
        }

        boolean recipeTerminated = !executeCommand(context, result, commandConfig);
        status.commandCompleted(result.getState());

        context.popTo(LABEL_EXECUTE);
        return recipeTerminated;
    }

    private boolean executeCommand(ExecutionContext context, CommandResult commandResult, CommandConfiguration commandConfig)
    {
        runningLock.lock();
        if (terminating)
        {
            runningLock.unlock();
            return false;
        }

        Command command = commandFactory.create(commandConfig);
        runningCommand = command;
        runningLock.unlock();

        commandResult.commence();
        long recipeId = context.getLong(NAMESPACE_INTERNAL, PROPERTY_RECIPE_ID, 0);
        eventManager.publish(new CommandCommencedEvent(this, recipeId, commandResult.getCommandName(), commandResult.getStartTime()));

        DefaultCommandContext commandContext = new DefaultCommandContext(context, commandResult, CommandResult.FEATURE_LIMIT_PER_FILE, postProcessorFactory);
        try
        {
            if (commandConfig.isEnabled())
            {
                executeAndProcess(commandContext, command, commandConfig);
            }
            else
            {
                commandResult.skip();
            }
        }
        catch (BuildException e)
        {
            commandResult.error(e);
        }
        catch (Exception e)
        {
            LOG.severe(e);
            commandResult.error("Unexpected error: " + e.getMessage());
        }
        finally
        {
            commandContext.addArtifactsToResult();

            runningLock.lock();
            runningCommand = null;
            runningLock.unlock();

            flushOutput(context);
            commandResult.complete();
            eventManager.publish(new CommandCompletedEvent(this, recipeId, commandResult));
        }

        return true;
    }

    private void executeAndProcess(DefaultCommandContext commandContext, Command command, CommandConfiguration commandConfig)
    {
        try
        {
            command.execute(commandContext);
        }
        finally
        {
            // still need to process any available artifacts, even in the event of an error.
            captureOutputs(commandConfig, commandContext);
            commandContext.processArtifacts();
        }
    }

    private void flushOutput(ExecutionContext context)
    {
        OutputStream outputStream = context.getOutputStream();
        if (outputStream != null)
        {
            try
            {
                outputStream.flush();
            }
            catch (IOException e)
            {
                LOG.severe(e);
            }
        }
    }

    private void captureOutputs(CommandConfiguration commandConfig, CommandContext context)
    {
        for (ArtifactConfiguration artifactConfiguration : commandConfig.getArtifacts().values())
        {
            try
            {
                Artifact artifact = artifactFactory.create(artifactConfiguration);
                artifact.capture(context);
            }
            catch (BuildException e)
            {
                context.error("Unable to capture output '" + artifactConfiguration.getName() + "': " + e.getMessage());
            }
            catch (Exception e)
            {
                String message = "Unexpected error capturing output '" + artifactConfiguration.getName() + "': " + e.getMessage();
                LOG.warning(message, e);
                context.error(message + " (check agent logs)");
            }
        }
    }

    public void terminateRecipe(long id) throws InterruptedException
    {
        // Preconditions:
        //   - this call is only made after the processor has sent the recipe
        //     commenced event
        // Responsibilities of this method:
        //   - after this call, no further command should be started
        //   - if a command is running during this call, it should be
        //     terminated
        runningLock.lock();
        try
        {
            // Check the id as it is possible for a request to come in after
            // the recipe has completed (which does no harm so long as we
            // don't terminate the next recipe!).
            if (runningRecipe == id)
            {
                terminating = true;
                if (runningCommand != null)
                {
                    runningCommand.terminate();
                }
            }
        }
        finally
        {
            runningLock.unlock();
        }
    }

    public void setEventManager(EventManager eventManager)
    {
        this.eventManager = eventManager;
    }

    public void setFileLoaderFactory(PulseFileLoaderFactory fileLoaderFactory)
    {
        this.fileLoaderFactory = fileLoaderFactory;
    }

    public void setIvyManager(IvyManager manager)
    {
        this.ivyManager = manager;
    }

    public void setCommandFactory(CommandFactory commandFactory)
    {
        this.commandFactory = commandFactory;
    }

    public void setPostProcessorFactory(PostProcessorFactory postProcessorFactory)
    {
        this.postProcessorFactory = postProcessorFactory;
    }

    public void setOutputFactory(ArtifactFactory artifactFactory)
    {
        this.artifactFactory = artifactFactory;
    }

    /**
     * The state of the recipe being processed.
     */
    private static class RecipeStatus
    {
        private ResultState state = ResultState.SUCCESS;
        private int commandIndex = 0;

        public ResultState getState()
        {
            return state;
        }

        public boolean isSuccess()
        {
            return state == ResultState.SUCCESS;
        }

        public int nextCommandIndex()
        {
            return commandIndex++;
        }

        public void commandCompleted(ResultState commandState)
        {
            state = ResultState.getWorseState(state, commandState);
        }
    }
}
