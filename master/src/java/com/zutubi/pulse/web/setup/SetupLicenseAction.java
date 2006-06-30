package com.zutubi.pulse.web.setup;

import com.zutubi.pulse.license.LicenseDecoder;
import com.zutubi.pulse.license.License;
import com.zutubi.pulse.license.LicenseException;
import com.zutubi.pulse.bootstrap.MasterConfigurationManager;

import java.io.IOException;

/**
 * <class-comment/>
 */
public class SetupLicenseAction extends SetupActionSupport
{
    private MasterConfigurationManager configurationManager;

    private String license;

    public String getLicense()
    {
        return license;
    }

    public void setLicense(String license)
    {
        this.license = license;
    }

    public void validate()
    {
        // take the license string, strip out any '\n' chars and check it.
        String licenseKey = license.replaceAll("\n", "");
        LicenseDecoder decoder = new LicenseDecoder();
        try
        {
            License l = decoder.decode(licenseKey.getBytes());
            if (l == null)
            {
                addFieldError("license", getText("license.key.invalid"));
                return;
            }
            if (l.isExpired())
            {
                addFieldError("license", getText("license.key.expired"));

            }
        }
        catch (LicenseException e)
        {
            addFieldError("license", getText("license.decode.error"));
        }
    }

    public String execute() throws Exception
    {
        String licenseKey = license.replaceAll("\n", "");
        try
        {
            configurationManager.getData().updateLicenseKey(licenseKey);
            setupManager.requestLicenseComplete();
            return SUCCESS;
        }
        catch (IOException e)
        {
            addActionError(e.getMessage());
            return INPUT;
        }
    }

    /**
     * Required resource.
     *
     * @param configurationManager
     */
    public void setConfigurationManager(MasterConfigurationManager configurationManager)
    {
        this.configurationManager = configurationManager;
    }
}
