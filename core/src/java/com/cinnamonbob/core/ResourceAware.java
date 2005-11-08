package com.cinnamonbob.core;

/**
 * Objects that implement ResourceAware have access to the resource
 * repository.
 */
public interface ResourceAware
{
    void setResourceRepository(ResourceRepository repository);
}
