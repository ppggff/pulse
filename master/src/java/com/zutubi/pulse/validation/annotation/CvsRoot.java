package com.zutubi.pulse.validation.annotation;

import com.zutubi.pulse.validation.validators.CvsRootValidator;
import com.zutubi.validation.annotations.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 *
 */
@Constraint(CvsRootValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CvsRoot
{
}
