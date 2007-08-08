package com.zutubi.pulse.scm;

import com.zutubi.pulse.core.model.Change;

import java.util.LinkedList;
import java.util.List;

/**
 */
public class ScmChangeAccumulator implements ScmEventHandler
{
    List<Change> changes = new LinkedList<Change>();

    public List<Change> getChanges()
    {
        return changes;
    }

    public void status(String message)
    {
    }

    public void fileChanged(Change change)
    {
        changes.add(change);
    }

    public void checkCancelled() throws ScmCancelledException
    {
    }
}
