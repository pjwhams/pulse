package com.zutubi.pulse.master.license.config;

import com.zutubi.config.annotations.Classification;
import com.zutubi.config.annotations.SymbolicName;
import com.zutubi.config.annotations.TextArea;
import com.zutubi.tove.config.AbstractConfiguration;
import com.zutubi.pulse.master.license.LicenseKey;

/**
 * A simple hashed key that holds the server license.
 */
@SymbolicName("zutubu.licenseConfig")
@Classification(single = "license")
public class LicenseConfiguration extends AbstractConfiguration
{
    @LicenseKey
    @TextArea(rows = 10, cols = 80)
    private String key;

    public LicenseConfiguration()
    {
        setPermanent(true);
    }

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }
}
