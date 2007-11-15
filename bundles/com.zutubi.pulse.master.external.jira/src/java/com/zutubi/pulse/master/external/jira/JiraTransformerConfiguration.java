package com.zutubi.pulse.master.external.jira;

import com.zutubi.config.annotations.*;
import com.zutubi.pulse.core.config.AbstractNamedConfiguration;
import com.zutubi.pulse.committransformers.Substitution;
import com.zutubi.pulse.prototype.config.project.commit.CommitMessageTransformerConfiguration;
import com.zutubi.util.CollectionUtils;
import com.zutubi.util.Mapping;
import com.zutubi.validation.annotations.Required;
import com.zutubi.validation.annotations.Url;

import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;

/**
 * A transformer that links Jira issue keys to the issues in a Jira install.
 */
@SymbolicName("zutubi.jiraTransformerConfig")
@Form(fieldOrder = {"name", "url", "matchAnyKey", "keys"})
public class JiraTransformerConfiguration extends CommitMessageTransformerConfiguration
{
    @Required
    @Url
    private String url;
    @ControllingCheckbox(dependentFields = {"keys"}, invert = true)
    private boolean matchAnyKey = true;
    @StringList
    private List<String> keys = new LinkedList<String>();

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public boolean isMatchAnyKey()
    {
        return matchAnyKey;
    }

    public void setMatchAnyKey(boolean matchAnyKey)
    {
        this.matchAnyKey = matchAnyKey;
    }

    public List<String> getKeys()
    {
        return keys;
    }

    public void setKeys(List<String> keys)
    {
        this.keys = keys;
    }

    public List<Substitution> substitutions()
    {
        final String replacement = getReplacement();
        return CollectionUtils.map(getKeyPatterns(), new Mapping<String, Substitution>()
        {
            public Substitution map(String keyPattern)
            {
                return new Substitution("(" + keyPattern + "-[0-9]+)", replacement);
            }
        });
    }

    @Transient
    private String getReplacement()
    {
        String url = getUrl();
        if (url.endsWith("/"))
        {
            url = url.substring(0, url.length() - 1);
        }

        return String.format("<a href='%s/browse/$1'>$1</a>", url);
    }

    @Transient
    private List<String> getKeyPatterns()
    {
        if(matchAnyKey)
        {
            return Arrays.asList("[A-Z]+");
        }
        else
        {
            return keys;
        }
    }
}
