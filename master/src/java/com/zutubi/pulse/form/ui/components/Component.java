package com.zutubi.pulse.form.ui.components;

import com.zutubi.pulse.form.ui.RenderContext;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

/**
 * <class-comment/>
 */
public class Component
{
    private String id;

    protected RenderContext context;

    protected Map<String, Object> parameters;

    private List<Component> nestedComponents;

    public void setContext(RenderContext context)
    {
        this.context = context;
    }

    public boolean start() throws Exception
    {
        return true;
    }

    public boolean end() throws Exception
    {
        return false;
    }

    public List<Component> getNestedComponents()
    {
        if (nestedComponents == null)
        {
            nestedComponents = new LinkedList<Component>();
        }
        return nestedComponents;
    }

    public void addNestedComponent(Component component)
    {
        getNestedComponents().add(component);
    }

    protected Map<String, Object> getParameters()
    {
        if (parameters == null)
        {
            parameters = new HashMap<String, Object>();
        }
        return parameters;
    }

    public void addParameter(String key, Object value)
    {
        getParameters().put(key, value);
    }

    public void addParameters(Map<String, Object> params)
    {
        getParameters().putAll(params);
    }

    public Component getNestedComponent(String id)
    {
        if (id == null)
        {
            return null;
        }
        for (Component component : getNestedComponents())
        {
            if (id.equals(component.getId()))
            {
                return component;
            }
        }
        return null;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }
}
