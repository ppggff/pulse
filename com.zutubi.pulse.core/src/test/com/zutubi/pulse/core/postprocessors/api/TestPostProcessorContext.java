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

package com.zutubi.pulse.core.postprocessors.api;

import com.zutubi.pulse.core.engine.api.ExecutionContext;
import com.zutubi.pulse.core.engine.api.Feature;
import com.zutubi.pulse.core.engine.api.FieldScope;
import com.zutubi.pulse.core.engine.api.ResultState;
import com.zutubi.util.adt.Pair;

import java.util.*;

import static com.zutubi.util.CollectionUtils.asPair;

/**
 * An implementation of {@link com.zutubi.pulse.core.postprocessors.api.PostProcessorContext}
 * that remembers and provides access to the data registered against it, for
 * use in testing.
 *
 * @see com.zutubi.pulse.core.postprocessors.api.PostProcessorTestCase
 */
public class TestPostProcessorContext implements PostProcessorContext
{
    private ExecutionContext executionContext;
    private ResultState resultState;
    private TestSuiteResult testSuiteResult = new TestSuiteResult(null);
    private List<Feature> features = new LinkedList<Feature>();
    private List<Feature> commandFeatures = new LinkedList<Feature>();
    private Map<Pair<FieldScope, String>, String> customFields = new HashMap<Pair<FieldScope, String>, String>();

    /**
     * Creates a context that contains the given execution context and has the
     * result state initialised to {@link ResultState#SUCCESS}.
     *
     * @param executionContext execution context to contain
     */
    public TestPostProcessorContext(ExecutionContext executionContext)
    {
        this(executionContext, ResultState.SUCCESS);
    }

    /**
     * Creates a context that contains the given execution context and has the
     * result state initialised to the given value.
     *
     * @param executionContext execution context to contain
     * @param resultState      the initial result state
     */
    public TestPostProcessorContext(ExecutionContext executionContext, ResultState resultState)
    {
        this.executionContext = executionContext;
        this.resultState = resultState;
    }

    public ExecutionContext getExecutionContext()
    {
        return executionContext;
    }

    public ResultState getResultState()
    {
        return resultState;
    }

    /**
     * Returns the root test suite result.  This suite has a null name, and
     * contains all suites and cases found by post-processing so far.
     *
     * @return the root test suite result
     */
    public TestSuiteResult getTestSuiteResult()
    {
        return testSuiteResult;
    }

    public void addTests(TestSuiteResult suite, NameConflictResolution conflictResolution)
    {
        testSuiteResult.addAllSuites(suite.getSuites());
        testSuiteResult.addAllCases(suite.getCases());
    }

    /**
     * Returns all features collected for the artifact so far.  These are the
     * features added by {@link #addFeature(Feature)}.
     *
     * @return all features collected for the artifact
     */
    public List<Feature> getFeatures()
    {
        return Collections.unmodifiableList(features);
    }

    public void addFeature(Feature feature)
    {
        features.add(feature);
    }

    /**
     * Returns all features collected for the command so far.  These are the
     * features added by {@link #addFeatureToCommand(Feature)} and the
     * associated methods {@link #failCommand(String)} and
     * {@link #errorCommand(String)}.
     * 
     * @return all features collected for the command
     */
    public List<Feature> getCommandFeatures()
    {
        return Collections.unmodifiableList(commandFeatures);
    }

    public void failCommand(String message)
    {
        addFeatureToCommand(new Feature(Feature.Level.ERROR, message));
        resultState = ResultState.FAILURE;
    }

    public void errorCommand(String message)
    {
        addFeatureToCommand(new Feature(Feature.Level.ERROR, message));
        resultState = ResultState.ERROR;
    }

    public void addFeatureToCommand(Feature feature)
    {
        commandFeatures.add(feature);
    }

    /**
     * Returns all custom fields collected so far.  These are fields added by
     * {@link #addCustomField(FieldScope, String, String)}.
     *
     * @return all custom fields added to the recipe result so far
     */
    public Map<Pair<FieldScope, String>, String> getCustomFields()
    {
        return customFields;
    }

    public void addCustomField(FieldScope scope, String name, String value)
    {
        customFields.put(asPair(scope, name), value);
    }
}
