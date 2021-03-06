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

package com.zutubi.pulse.acceptance.pages.browse;

import com.zutubi.pulse.acceptance.SeleniumBrowser;
import com.zutubi.pulse.acceptance.pages.SeleniumPage;
import com.zutubi.pulse.master.webwork.Urls;
import org.openqa.selenium.By;

/**
 * Abstract base for pages that show the user responsible for a build.
 */
public abstract class ResponsibilityPage extends SeleniumPage
{
    private static final String ID_RESPONSIBLE_PANEL = "responsible-panel";
    private static final String ID_RESPONSIBLE_MESSAGE = "responsible-message";
    private static final String ID_RESPONSIBLE_CLEAR = "responsible-clear";
    private static final String ID_RESPONSIBLE_COMMENT = "responsible-comment";

    public ResponsibilityPage(SeleniumBrowser browser, Urls urls, String id, String title)
    {
        super(browser, urls, id, title);
    }

    public boolean hasResponsibleUser()
    {
        try
        {
            return browser.isVisible(By.id(ID_RESPONSIBLE_PANEL));
        }
        catch (Exception e)
        {
            // If the element is expected to be removed from the page, we may
            // get a selenium error (isVisible demands the id exist, we can't
            // be sure it will even if we check just before hand).
            return false;
        }
    }

    public String getResponsibleMessage()
    {
        return browser.getText(By.id(ID_RESPONSIBLE_MESSAGE));
    }

    public String getResponsibleComment()
    {
        if (browser.isElementIdPresent(ID_RESPONSIBLE_COMMENT))
        {
            return browser.getText(By.id(ID_RESPONSIBLE_COMMENT));
        }
        else
        {
            return "";
        }
    }

    public void clickClearResponsible()
    {
        browser.click(By.id(ID_RESPONSIBLE_CLEAR));
    }

    public boolean isClearResponsibilityPresent()
    {
        return browser.isElementIdPresent(ID_RESPONSIBLE_CLEAR);
    }

    protected String getActionId(String actionName)
    {
        return "action." + actionName;
    }

    public boolean isActionPresent(String actionName)
    {
        return browser.isElementIdPresent(getActionId(actionName));
    }

    public void clickAction(String actionName)
    {
        browser.click(By.id(getActionId(actionName)));
    }
}
