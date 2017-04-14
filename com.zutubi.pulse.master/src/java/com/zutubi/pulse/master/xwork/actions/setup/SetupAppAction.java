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

package com.zutubi.pulse.master.xwork.actions.setup;

import com.zutubi.pulse.master.bootstrap.WebManager;
import com.zutubi.pulse.master.xwork.actions.ActionSupport;

/**
 * Redirects to the correct place depending on whether we are still starting/setting up or not.
 */
public class SetupAppAction extends ActionSupport
{
    private WebManager webManager;

    public String execute() throws Exception
    {
        if (webManager.isMainDeployed())
        {
            return "redirect";
        }
        else
        {
            return "success";
        }
    }

    public void setWebManager(WebManager webManager)
    {
        this.webManager = webManager;
    }
}