/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.master.tove.config.user;

import com.zutubi.i18n.Messages;
import com.zutubi.pulse.master.model.UserManager;
import com.zutubi.tove.annotations.*;
import com.zutubi.tove.config.api.AbstractConfiguration;
import com.zutubi.validation.Validateable;
import com.zutubi.validation.ValidationContext;
import com.zutubi.validation.annotations.Required;

/**
 * Transient configuration that allows a user to be created via self-signup.
 */
@SymbolicName("zutubi.signupUserConfig")
@Form(fieldOrder = {"login", "name", "password", "confirmPassword"})
@Wire
public class SignupUserConfiguration extends AbstractConfiguration implements Validateable
{
    private static final Messages I18N = Messages.getInstance(SignupUserConfiguration.class);

    @Required
    private String login;
    @Required
    private String name;
    @Password
    private String password;
    @Password
    private String confirmPassword;

    @Transient
    private UserManager userManager;

    public String getLogin()
    {
        return login;
    }

    public void setLogin(String login)
    {
        this.login = login;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getConfirmPassword()
    {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword)
    {
        this.confirmPassword = confirmPassword;
    }

    public void validate(ValidationContext context)
    {
        if(login != null && userManager.getUser(login) != null)
        {
            context.addFieldError("login", "login '" + login + "' is already in use; please choose another login");
        }
        
        if(password != null && confirmPassword != null)
        {
            if(!password.equals(confirmPassword))
            {
                context.addFieldError("password", I18N.format("passwords.differ"));
            }
        }
    }

    public void setUserManager(UserManager userManager)
    {
        this.userManager = userManager;
    }
}
