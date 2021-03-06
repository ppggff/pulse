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

package com.zutubi.pulse.master.upgrade.tasks;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.zutubi.tove.type.record.Record;

import java.util.List;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.toArray;
import static com.zutubi.tove.type.record.PathUtils.WILDCARD_ANY_ELEMENT;
import static com.zutubi.tove.type.record.PathUtils.getPath;
import static java.util.Arrays.asList;

/**
 * Fixes null post-processor references that may have been created by a bug in
 * smart clone.  See CIB-2562.
 */
public class FixNullPostProcessorReferencesUpgradeTask extends AbstractRecordPropertiesUpgradeTask
{
    private static final String SCOPE_PROJECTS = "projects";
    private static final String PROPERTY_TYPE = "type";
    private static final String PROPERTY_RECIPES = "recipes";
    private static final String PROPERTY_COMMANDS = "commands";
    private static final String PROPERTY_ARTIFACTS = "artifacts";
    private static final String PROPERTY_POST_PROCESSORS = "postProcessors";

    public boolean haltOnFailure()
    {
        return true;
    }

    protected RecordLocator getRecordLocator()
    {
        RecordLocator locator = RecordLocators.newUnion(
                RecordLocators.newPathPattern(getPath(SCOPE_PROJECTS, WILDCARD_ANY_ELEMENT, PROPERTY_TYPE, PROPERTY_RECIPES, WILDCARD_ANY_ELEMENT, PROPERTY_COMMANDS, WILDCARD_ANY_ELEMENT)),
                RecordLocators.newPathPattern(getPath(SCOPE_PROJECTS, WILDCARD_ANY_ELEMENT, PROPERTY_TYPE, PROPERTY_RECIPES, WILDCARD_ANY_ELEMENT, PROPERTY_COMMANDS, WILDCARD_ANY_ELEMENT, PROPERTY_ARTIFACTS, WILDCARD_ANY_ELEMENT))
        );
        
        return new PredicateFilterRecordLocator(locator, new Predicate<Record>()
        {
            public boolean apply(Record record)
            {
                return record.containsKey(PROPERTY_POST_PROCESSORS);
            }
        });
    }

    protected List<? extends RecordUpgrader> getRecordUpgraders()
    {
        return asList(RecordUpgraders.newEditProperty(PROPERTY_POST_PROCESSORS, new Function<Object, Object>()
        {
            public Object apply(Object o)
            {
                if (o != null && o instanceof String[])
                {
                    String[] array = (String[]) o;
                    return toArray(filter(asList(array), not(equalTo("0"))), String.class);
                }
                
                return o;
            }
        }));
    }
}