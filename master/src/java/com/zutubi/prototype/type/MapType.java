package com.zutubi.prototype.type;

import com.zutubi.prototype.annotation.ID;
import com.zutubi.prototype.type.record.Record;

import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 */
public class MapType extends CollectionType
{
    public MapType()
    {
        this(HashMap.class);
    }

    public MapType(Class type)
    {
        super(type);
    }

    public MapType(Class type, String symbolicName)
    {
        super(type, symbolicName);
    }

    public Map instantiate(Object data) throws TypeException
    {
        if (data == null)
        {
            return null;
        }

        if (!Map.class.isAssignableFrom(data.getClass()))
        {
            throw new TypeConversionException("Expected a map type, instead received " + data.getClass());
        }

        Record record = (Record) data;

        Type defaultType = getCollectionType();
        if (defaultType == null && record.getMeta("type") != null)
        {
            defaultType = typeRegistry.getType(record.getMeta("type"));
        }

        Map<String, Object> instance = new HashMap<String, Object>();
        for (String key : record.keySet())
        {
            Object child = record.get(key);
            Type type = defaultType;
            if (child instanceof Record)
            {
                Record childRecord = (Record) child;
                type = typeRegistry.getType(childRecord.getSymbolicName());
            }

            Object value = type.instantiate(child);
            instance.put(key, value);
        }

        return instance;
    }

    public TypeProperty getKeyProperty(Object obj)
    {
        // TODO: assumes a Map only holds composites, which is fair enough but
        // would need to be enforced at registration time.
        CompositeType type = (CompositeType) getCollectionType();
        for (TypeProperty property : type.getProperties(PrimitiveType.class))
        {
            if (property.getAnnotation(ID.class) != null)
            {
                return property;
            }
        }
        return null;
    }
}
