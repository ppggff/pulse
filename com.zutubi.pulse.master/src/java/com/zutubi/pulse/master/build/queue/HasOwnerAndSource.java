package com.zutubi.pulse.master.build.queue;

import com.zutubi.util.Predicate;
import com.zutubi.util.StringUtils;
import com.zutubi.pulse.master.events.build.BuildRequestEvent;

/**
 * A predicate that matches a request holder containing a
 * build request event with a specified owner and options source field.
 *
 * @param <T> the specific subclass of RequestHolder that is being searched.
 */
public class HasOwnerAndSource<T extends RequestHolder> implements Predicate<T>
{
    private Object owner;
    private String source;

    public HasOwnerAndSource(Object owner, String source)
    {
        this.owner = owner;
        this.source = source;
    }

    public boolean satisfied(RequestHolder holder)
    {
        BuildRequestEvent request = holder.getRequest();
        return request.getOwner().equals(owner) && StringUtils.equals(request.getOptions().getSource(), source);
    }
}
