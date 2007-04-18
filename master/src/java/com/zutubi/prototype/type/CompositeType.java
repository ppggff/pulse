package com.zutubi.prototype.type;

import com.zutubi.prototype.config.ConfigurationPersistenceManager;
import com.zutubi.prototype.type.record.*;
import com.zutubi.pulse.prototype.squeezer.SqueezeException;
import com.zutubi.pulse.prototype.squeezer.Squeezers;
import com.zutubi.pulse.prototype.squeezer.TypeSqueezer;
import com.zutubi.pulse.util.CollectionUtils;
import com.zutubi.pulse.util.Mapping;
import com.zutubi.pulse.util.logging.Logger;

import java.lang.reflect.Method;
import java.util.*;

/**
 *
 */
public class CompositeType extends AbstractType implements ComplexType
{
    private static final Logger LOG = Logger.getLogger(CompositeType.class);

    private List<String> extensions = new LinkedList<String>();
    private Map<String, TypeProperty> properties = new HashMap<String, TypeProperty>();
    private Map<Class, List<String>> propertiesByClass = new HashMap<Class, List<String>>();

    private ConfigurationPersistenceManager configurationPersistenceManager;

    public CompositeType(Class type, String symbolicName, ConfigurationPersistenceManager configurationPersistenceManager)
    {
        super(type, symbolicName);
        this.configurationPersistenceManager = configurationPersistenceManager;
    }

    public void addProperty(TypeProperty property)
    {
        this.properties.put(property.getName(), property);
        Class typeClass = property.getType().getClass();
        if (!propertiesByClass.containsKey(typeClass))
        {
            propertiesByClass.put(typeClass, new LinkedList<String>());
        }
        List<String> props = propertiesByClass.get(typeClass);
        props.add(property.getName());

    }

    public List<TypeProperty> getProperties()
    {
        return Collections.unmodifiableList(new LinkedList<TypeProperty>(properties.values()));
    }

    public TypeProperty getProperty(String propertyName)
    {
        return properties.get(propertyName);
    }

    /**
     * Retrieve all of the property names of properties of a specific type.
     *
     * @param type being retrieved.
     * @return a list of property names, or an empty list if no properties of the requested type can
     *         be located.
     */
    public List<TypeProperty> getProperties(Class<? extends Type> type)
    {
        List<TypeProperty> result = new LinkedList<TypeProperty>();
        for (Map.Entry<Class, List<String>> entry : propertiesByClass.entrySet())
        {
            if (type.isAssignableFrom(entry.getKey()))
            {
                for (String property : entry.getValue())
                {
                    result.add(getProperty(property));
                }
            }
        }
        return result;
    }

    public List<String> getPropertyNames(Class<? extends Type> type)
    {
        return CollectionUtils.map(getProperties(type), new Mapping<TypeProperty, String>()
        {
            public String map(TypeProperty property)
            {
                return property.getName();
            }
        });
    }

    public List<String> getPropertyNames()
    {
        return Collections.unmodifiableList(new LinkedList<String>(properties.keySet()));
    }

    public boolean hasProperty(String propertyName)
    {
        return properties.containsKey(propertyName);
    }

    public void addExtension(String symbolicName)
    {
        this.extensions.add(symbolicName);
    }

    public List<String> getExtensions()
    {
        return Collections.unmodifiableList(extensions);
    }

    public void setExtensions(List<String> extensions)
    {
        this.extensions = extensions;
    }

    public Object instantiate(String path, Object data) throws TypeException
    {
        Object instance = path == null ? null : configurationPersistenceManager.getInstance(path);
        if (instance == null && data != null)
        {
            try
            {
                Record record = (Record) data;

                instance = getClazz().newInstance();
                if (path != null)
                {
                    configurationPersistenceManager.putInstance(path, instance);
                }

                TypeConversionException exception = null;

                for (Map.Entry<String, TypeProperty> entry : properties.entrySet())
                {
                    String name = entry.getKey();
                    if (!record.containsKey(name))
                    {
                        continue;
                    }

                    TypeProperty property = entry.getValue();

                    // Instantiate even if there is no setter so the instance
                    // is both checked for validity and cached.
                    Type type = property.getType();
                    Object value = type.instantiate(path == null ? null : PathUtils.getPath(path, name), record.get(name));
                    Method setter = property.getSetter();
                    if (setter != null)
                    {
                        try
                        {
                            if (value != null || !(type instanceof PrimitiveType))
                            {
                                setter.invoke(instance, value);
                            }
                        }
                        catch (Exception e)
                        {
                            if (exception == null)
                            {
                                exception = new TypeConversionException();
                            }
                            exception.addFieldError(name, e.getMessage());
                        }
                    }
                }

                if (exception != null)
                {
                    throw exception;
                }

            }
            catch (TypeConversionException e)
            {
                // let the type conversion exception pass through.
                throw e;
            }
            catch (Exception e)
            {
                throw new TypeConversionException(e);
            }
        }

        return instance;
    }

    public Object unstantiate(Object instance) throws TypeException
    {
        MutableRecord result = createNewRecord(false);
        for(TypeProperty property: getProperties())
        {
            final Method getter = property.getGetter();
            if(getter != null)
            {
                try
                {
                    Object value = getter.invoke(instance);
                    result.put(property.getName(), property.getType().unstantiate(value));
                }
                catch (Exception e)
                {
                    throw new TypeException("Unable to invoke getter for property '" + property.getName() + "': " + e.getMessage(), e);
                }
            }
        }
        
        return result;
    }

    public String insert(String path, Record newRecord, RecordManager recordManager)
    {
        recordManager.insert(path, newRecord);
        return path;
    }

    public String save(String path, String baseName, Record record, RecordManager recordManager)
    {
        // Nothing special to do, let the record manager figure it out.
        String newPath = PathUtils.getPath(path, baseName);
        recordManager.insertOrUpdate(newPath, record);
        return newPath;
    }

    public MutableRecord createNewRecord()
    {
        return createNewRecord(true);
    }

    private MutableRecord createNewRecord(boolean initialise)
    {
        if(extensions.size() > 0)
        {
            // Can only be created when the extension type is specified,
            // there is no default initialisation.
            return null;
        }

        MutableRecordImpl record = new MutableRecordImpl();
        for(TypeProperty property: getProperties(ComplexType.class))
        {
            ComplexType type = (ComplexType) property.getType();
            Record childRecord = type.createNewRecord();
            if (childRecord != null)
            {
                record.put(property.getName(), childRecord);
            }
        }
        record.setSymbolicName(getSymbolicName());

        if (initialise)
        {
            // setup the default values, instantiate the configuration object and
            // extract the default values.
            try
            {
                Object defaultInstance = getClazz().newInstance();
                for (TypeProperty property : getProperties(SimpleType.class))
                {
                    try
                    {
                        TypeSqueezer squeezer = Squeezers.findSqueezer(property.getClazz());
                        record.put(property.getName(), squeezer.squeeze(property.getGetter().invoke(defaultInstance)));
                    }
                    catch (SqueezeException e)
                    {
                        LOG.warning(e);
                    }
                }
            }
            catch (Exception e)
            {
                LOG.warning(e);
            }
        }

        return record;
    }

    public boolean isTemplated()
    {
        return false;
    }

    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        CompositeType that = (CompositeType) o;
        return getSymbolicName().equals(that.getSymbolicName());
    }

    public int hashCode()
    {
        return getSymbolicName().hashCode();
    }
}