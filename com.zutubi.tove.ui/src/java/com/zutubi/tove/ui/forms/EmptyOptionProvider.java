package com.zutubi.tove.ui.forms;

import com.zutubi.tove.type.TypeProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * An option provider that provides no options.
 */
public class EmptyOptionProvider extends MapOptionProvider
{
    public Option getEmptyOption(TypeProperty property, FormContext context)
    {
        return new Option("", "");
    }

    public Map<String,String> getMap(TypeProperty property, FormContext context)
    {
        return new HashMap<>();
    }
}