package com.zutubi.pulse.master.upgrade.tasks;

import com.zutubi.tove.type.record.PathUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Adds a new option to PerforceConfiguration to allow selection of ticket
 * authentication.
 */
public class AddPerforceTicketAuthenticationUpgradeTask extends AbstractRecordPropertiesUpgradeTask
{
    private static final String SCOPE_PROJECTS = "projects";
    private static final String TYPE_PERFORCE = "zutubi.perforceConfig";
    private static final String PROPERTY_SCM = "scm";
    private static final String PROPERTY_TICKET_AUTH = "useTicketAuth";

    public String getName()
    {
        return "Add Perforce Ticket Authentication";
    }

    public String getDescription()
    {
        return "Adds a new option to Perforce projects to allow use of ticket authentication";
    }

    public boolean haltOnFailure()
    {
        return true;
    }

    protected RecordLocator getRecordLocator()
    {
        return RecordLocators.newTypeFilter(
                RecordLocators.newPathPattern(PathUtils.getPath(SCOPE_PROJECTS, PathUtils.WILDCARD_ANY_ELEMENT, PROPERTY_SCM)),
                TYPE_PERFORCE
        );
    }

    protected List<RecordUpgrader> getRecordUpgraders()
    {
        return Arrays.asList(RecordUpgraders.newAddProperty(PROPERTY_TICKET_AUTH, Boolean.toString(false)));
    }
}