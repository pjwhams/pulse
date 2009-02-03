package com.zutubi.pulse.core.commands.api;

import com.zutubi.validation.annotations.Required;

/**
 */
public class LinkOutputConfiguration extends OutputConfigurationSupport
{
    @Required
    private String url;

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public LinkOutput createOutput()
    {
        return buildOutput(LinkOutput.class);
    }
}