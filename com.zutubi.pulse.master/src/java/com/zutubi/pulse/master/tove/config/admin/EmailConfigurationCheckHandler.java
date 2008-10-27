package com.zutubi.pulse.master.tove.config.admin;

import com.zutubi.config.annotations.SymbolicName;
import com.zutubi.tove.config.ConfigurationCheckHandlerSupport;
import com.zutubi.pulse.master.tove.config.user.contacts.EmailContactConfiguration;
import com.zutubi.validation.annotations.Required;

import java.util.Arrays;

/**
 */
@SymbolicName("zutubi.emailConfigurationCheckHandler")
public class EmailConfigurationCheckHandler extends ConfigurationCheckHandlerSupport<EmailConfiguration>
{
    @Required
    private String emailAddress;

    public void test(EmailConfiguration configuration) throws Exception
    {
        EmailContactConfiguration.sendMail(Arrays.asList(emailAddress), configuration, "Test Email", "text/plain", "Welcome to Zutubi Pulse!");
    }

    public String getEmailAddress()
    {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress)
    {
        this.emailAddress = emailAddress;
    }
}
