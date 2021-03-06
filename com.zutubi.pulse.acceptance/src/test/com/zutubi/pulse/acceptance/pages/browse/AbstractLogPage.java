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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * base log page.
 */
public abstract class AbstractLogPage extends SeleniumPage 
{
    private static final String ID_DOWNLOAD_FULL_LOG = "download-full-log";
    private static final String ID_SETTINGS = "current-settings";
    private static final String ID_CONFIGURE = "configure-settings";

    protected AbstractLogPage(SeleniumBrowser browser, Urls urls, String id)
    {
        super(browser, urls, id);
    }
    public boolean isDownloadLinkAvailable()
    {
        return browser.isElementIdPresent(ID_DOWNLOAD_FULL_LOG);
    }

    public void clickDownloadLink()
    {
        browser.click(By.id(ID_DOWNLOAD_FULL_LOG));
    }

    /**
     * Retrieve the visible log text
     *
     * @return log text.
     */
    public String getLog()
    {
        return browser.getText(By.id(getId()));
    }

    public boolean logContains(String text)
    {
        return getLog().contains(text);
    }

    public boolean isLogAvailable()
    {
        return browser.isElementIdPresent(getId()) && !browser.isTextPresent("log file does not exist");
    }

    public boolean isLogNotAvailable()
    {
        return browser.isElementIdPresent(getId()) && browser.isTextPresent("log file does not exist");
    }

    /**
     * Clicks the configure link, waits for the settings dialog to appear, and
     * returns said dialog.
     *
     * @return the tail settings dialog popped up by clicking "configure"
     */
    public TailSettingsDialog clickConfigureAndWaitForDialog()
    {
        browser.click(By.id(ID_CONFIGURE));
        TailSettingsDialog tailSettingsDialog = new TailSettingsDialog(browser);
        tailSettingsDialog.waitFor();
        return tailSettingsDialog;
    }

    /**
     * Gets the current max lines settings, by extracting it from the page text.
     *
     * @return the current max lines setting
     */
    public int getMaxLines()
    {
        Pattern pattern = Pattern.compile("max (\\d+) lines");
        String text = browser.getText(By.id(ID_SETTINGS));
        Matcher matcher = pattern.matcher(text);
        matcher.find();
        return Integer.parseInt(matcher.group(1));
    }
}
