package com.zutubi.pulse.master.upgrade.tasks;

import com.zutubi.tove.type.record.PathUtils;
import com.zutubi.util.UnaryFunction;

import java.util.Arrays;
import java.util.List;

/**
 * Updates the syncWorkspacePattern field in the perforce configuration to
 * include the stage handle (if it is unchanged from its default).
 */
public class UpdatePerforceSyncWorkspacePatternUpgradeTask extends AbstractRecordPropertiesUpgradeTask
{
    private static final String PROPERTY = "syncWorkspacePattern";
    private static final String PREVIOUS_DEFAULT_VALUE = System.getProperty("pulse.p4.client.prefix", "pulse-") + "$(project.handle)-$(agent.handle)";
    private static final String NEW_DEFAULT_VALUE = System.getProperty("pulse.p4.client.prefix", "pulse-") + "$(project.handle)-$(stage.handle)-$(agent.handle)";

    protected RecordLocator getRecordLocator()
    {
        return RecordLocators.newTypeFilter(RecordLocators.newPathPattern(PathUtils.getPath("projects", PathUtils.WILDCARD_ANY_ELEMENT, "scm")), "zutubi.perforceConfig");
    }

    protected List<RecordUpgrader> getRecordUpgraders()
    {
        return Arrays.asList(RecordUpgraders.newEditProperty(PROPERTY, new UnaryFunction<Object, Object>()
        {
            public Object process(Object currentValue)
            {
                if (PREVIOUS_DEFAULT_VALUE.equals(currentValue))
                {
                    return NEW_DEFAULT_VALUE;
                }
                else
                {
                    return currentValue;
                }
            }
        }));
    }

    public boolean haltOnFailure()
    {
        return false;
    }
}