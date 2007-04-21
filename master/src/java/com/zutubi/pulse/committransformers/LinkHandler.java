package com.zutubi.pulse.committransformers;

import com.zutubi.config.annotations.Form;
import com.zutubi.config.annotations.Text;
import com.zutubi.validation.annotations.Pattern;
import com.zutubi.validation.annotations.Required;

/**
 * <class comment/>
 */
@Form(fieldOrder = {"name", "expression", "link"})
public class LinkHandler implements CommitMessageHandler
{
    private String name;
    private String expression;
    private String link;
    
    @Required @Text(size=50)
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @Required @Pattern @Text(size=50)
    public String getExpression()
    {
        return expression;
    }

    public void setExpression(String expression)
    {
        this.expression = expression;
    }

    @Required @Text(size=50)
    public String getLink()
    {
        return link;
    }

    public void setLink(String link)
    {
        this.link = link;
    }

}
