package com.zutubi.pulse.prototype.config.user;

import com.zutubi.config.annotations.Form;
import com.zutubi.config.annotations.Select;
import com.zutubi.config.annotations.SymbolicName;

/**
 * A subscription to results for personal builds.
 */
@SymbolicName("zutubi.personalSubscriptionConfig")
@Form(fieldOrder = {"name", "contact", "template"})
public class PersonalSubscriptionConfiguration extends SubscriptionConfiguration
{
    @Select(optionProvider = "SubscriptionTemplateOptionProvider")
    private String template;

    public String getTemplate()
    {
        return template;
    }

    public void setTemplate(String template)
    {
        this.template = template;
    }
}
