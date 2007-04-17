package com.zutubi.pulse.web.admin.user;

import com.zutubi.pulse.model.AcegiUser;
import com.zutubi.pulse.security.AcegiUtils;
import com.zutubi.pulse.prototype.config.admin.GeneralAdminConfiguration;
import com.zutubi.prototype.config.ConfigurationProvider;

/**
 */
public class SignupUserAction extends CreateUserAction
{
    private ConfigurationProvider configurationProvider;

    public String execute() throws Exception
    {
        if (!configurationProvider.get(GeneralAdminConfiguration.class).isAnonymousSignupEnabled())
        {
            return "disabled";
        }

        getUserManager().addUser(newUser, false, false);

        // log the new user in.
        AcegiUtils.loginAs(new AcegiUser(newUser));

        return SUCCESS;
    }

    public void setConfigurationProvider(ConfigurationProvider configurationProvider)
    {
        this.configurationProvider = configurationProvider;
    }
}
