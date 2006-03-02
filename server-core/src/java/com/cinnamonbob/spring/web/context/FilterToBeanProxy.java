package com.cinnamonbob.spring.web.context;

import org.springframework.context.ApplicationContext;

import javax.servlet.FilterConfig;

import com.cinnamonbob.bootstrap.ComponentContext;

/**
 * <class-comment/>
 */
public class FilterToBeanProxy extends org.acegisecurity.util.FilterToBeanProxy
{
    protected ApplicationContext getContext(FilterConfig filterConfig)
    {
        // can not autowire since this object is created by Jetty.
        return ComponentContext.getContext();
    }
}
