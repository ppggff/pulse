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

package com.zutubi.tove.type;

import com.zutubi.tove.annotations.ID;
import com.zutubi.tove.config.ConfigurationReferenceManager;
import com.zutubi.tove.config.api.Configuration;
import com.zutubi.util.logging.Logger;
import com.zutubi.util.reflection.AnnotationUtils;

import java.beans.IntrospectionException;

/**
 * A type that represents a reference to some composite type.  The reference
 * value itself is just a path, which resolves to a record stored at that
 * location.
 */
public class ReferenceType extends SimpleType implements Type
{
    private static final Logger LOG = Logger.getLogger(ReferenceType.class);

    public static final String NULL_REFERENCE = "0";
    
    private CompositeType referencedType;
    private ConfigurationReferenceManager configurationReferenceManager;
    private String idProperty;

    public ReferenceType(CompositeType referencedType, ConfigurationReferenceManager configurationReferenceManager) throws TypeException
    {
        super(referencedType.getClazz());
        this.referencedType = referencedType;
        this.configurationReferenceManager = configurationReferenceManager;

        try
        {
            idProperty = AnnotationUtils.getPropertyAnnotatedWith(referencedType.getClazz(), ID.class);
        }
        catch (IntrospectionException e)
        {
            LOG.severe(e);
        }

        if(idProperty == null)
        {
            throw new TypeException("Referenced types must have an ID property");
        }
    }

    public CompositeType getReferencedType()
    {
        return referencedType;
    }

    public String getIdProperty()
    {
        return idProperty;
    }

    public Configuration instantiate(Object data, Instantiator instantiator) throws TypeException
    {
        long handle = getHandle(data);
        if(handle > 0)
        {
            return instantiator.resolveReference(handle);
        }
        else
        {
            // Zero or negative handle == null reference.
            return null;
        }
    }

    public long getHandle(Object data) throws TypeException
    {
        String referenceHandle = (String) data;
        try
        {
            return Long.parseLong(referenceHandle);
        }
        catch (NumberFormatException e)
        {
            throw new TypeException("Illegal reference '" + referenceHandle + "'");
        }
    }

    public Object unstantiate(Object instance, String templateOwnerPath) throws TypeException
    {
        if (instance == null)
        {
            return NULL_REFERENCE;
        }
        else
        {
            String referencedPath = ((Configuration) instance).getConfigurationPath();
            if (referencedPath == null)
            {
                // This should not be possible via the UI, as the user has no
                // way to select a non-persistent (i.e. no handle) instance
                // as the value of a reference.  It is possible
                // programatically, but can be worked around.
                throw new TypeException("Attempt to unstantiate a reference to an instance that is not yet persistent.  Ensure the referee is persistent before saving a reference to it.");
            }

            return Long.toString(configurationReferenceManager.getReferenceHandleForPath(templateOwnerPath, referencedPath));
        }
    }

    public String getReferencedPath(String templateOwnerPath, Object data) throws TypeException
    {
        long handle = getHandle(data);
        if(handle > 0)
        {
            return configurationReferenceManager.getReferencedPathForHandle(templateOwnerPath, handle);
        }
        else
        {
            return null;
        }
    }

    public Object toXmlRpc(String templateOwnerPath, Object data) throws TypeException
    {
        if (data == null)
        {
            return null;
        }
        else
        {
            // We return references via the remote api as paths so that the
            // caller can use the value in subsequent calls.
            return getReferencedPath(templateOwnerPath, data);
        }
    }

    public String fromXmlRpc(String templateOwnerPath, Object data, boolean applyDefaults) throws TypeException
    {
        if(data == null)
        {
            return NULL_REFERENCE;
        }
        else
        {
            typeCheck(data, String.class);
            String path = (String) data;
            long handle;
            if (path.length() == 0)
            {
                handle = 0;
            }
            else
            {
                handle = configurationReferenceManager.getReferenceHandleForPath(templateOwnerPath, path);
                if(handle == 0)
                {
                    throw new TypeException("Reference to unknown path '" + path + "'");
                }
            }
            return Long.toString(handle);
        }
    }

    public String toString()
    {
        return "reference[" + referencedType.toString() + "]";
    }
}
