package com.zutubi.prototype;

import com.zutubi.prototype.model.Field;
import com.zutubi.prototype.model.Form;
import com.zutubi.prototype.model.SubmitField;
import com.zutubi.pulse.util.CollectionUtils;
import com.zutubi.pulse.util.Predicate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 *
 *
 */
public class FormDescriptor implements Descriptor
{
    private List<FieldDescriptor> fieldDescriptors = new LinkedList<FieldDescriptor>();

    private String id;

    private Map<String, Object> parameters = new HashMap<String, Object>();

    private List<String> actions = new LinkedList<String>();

    public void setId(String id)
    {
        this.id = id;
    }

    public void add(FieldDescriptor descriptor)
    {
        fieldDescriptors.add(descriptor);
    }

    public FieldDescriptor getFieldDescriptor(final String name)
    {
        return CollectionUtils.find(fieldDescriptors, new Predicate<FieldDescriptor>()
        {
            public boolean satisfied(FieldDescriptor fieldDescriptor)
            {
                return fieldDescriptor.getName().equals(name);
            }
        });
    }

    public List<FieldDescriptor> getFieldDescriptors()
    {
        return fieldDescriptors;
    }

    public void setFieldDescriptors(List<FieldDescriptor> fieldDescriptors)
    {
        this.fieldDescriptors = fieldDescriptors;
    }

    public void addParameter(String key, Object value)
    {
        parameters.put(key, value);
    }

    public void addAll(Map<String, Object> parameters)
    {
        parameters.putAll(parameters);
    }

    public Map<String, Object> getParameters()
    {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters)
    {
        this.parameters = parameters;
    }

    public List<String> getActions()
    {
        return Collections.unmodifiableList(actions);
    }

    public void setActions(List<String> actions)
    {
        this.actions.clear();
        this.actions.addAll(actions);
    }

    public Form instantiate(Object data)
    {
        Form form = new Form();
        form.setId(id);    
        form.addAll(getParameters());
        List<String> fieldOrder = evaluateFieldOrder();

        int tabindex = 1;
        for (String fieldName : fieldOrder)
        {
            FieldDescriptor fieldDescriptor = getFieldDescriptor(fieldName);
            Field field = fieldDescriptor.instantiate(data);
            field.setTabindex(tabindex++);
            form.add(field);
        }

        // add the submit fields.
        for (String action : actions)
        {
            form.add(new SubmitField(action).setTabindex(tabindex++));
        }

        return form;
    }

    protected List<String> evaluateFieldOrder()
    {
        // If a field order is defined, lets us it as the starting point.
        LinkedList<String> ordered = new LinkedList<String>();
        if (parameters.containsKey("fieldOrder"))
        {
            ordered.addAll(Arrays.asList((String[])parameters.get("fieldOrder")));
        }

        // are we done?
        if (ordered.size() == getFieldDescriptors().size())
        {
            return ordered;
        }

        // add those fields that we have missed to the end of the list.
        for (FieldDescriptor fd : getFieldDescriptors())
        {
            if (!ordered.contains(fd.getName()))
            {
                ordered.addLast(fd.getName());
            }
        }
        return ordered;
    }

}
