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

package com.zutubi.pulse.master.tove.config;

import com.google.common.base.Predicate;
import static com.google.common.collect.Iterables.filter;
import static com.zutubi.pulse.master.tove.config.MasterConfigurationRegistry.PROJECTS_SCOPE;
import com.zutubi.tove.config.ConfigurationTemplateManager;
import com.zutubi.tove.transaction.TransactionManager;
import com.zutubi.tove.type.record.PathUtils;
import static com.zutubi.tove.type.record.PathUtils.WILDCARD_ANY_ELEMENT;

import java.util.Collection;

/**
 * Actions for labels.
 */
public class LabelConfigurationActions
{
    private ConfigurationTemplateManager configurationTemplateManager;
    private TransactionManager pulseTransactionManager;
    
    public NewLabelConfiguration prepareRename(LabelConfiguration instance)
    {
        return new NewLabelConfiguration(instance.getLabel());
    }
    
    public void doRename(final LabelConfiguration instance, final NewLabelConfiguration newName)
    {
        final String labelsPath = PathUtils.getPath(PROJECTS_SCOPE, WILDCARD_ANY_ELEMENT, "labels", WILDCARD_ANY_ELEMENT);
        final Collection<LabelConfiguration> allLabels = configurationTemplateManager.getAllInstances(labelsPath, LabelConfiguration.class, false);
        final Iterable<LabelConfiguration> matchingLabels = filter(allLabels, new Predicate<LabelConfiguration>()
        {
            public boolean apply(LabelConfiguration l)
            {
                return l.getLabel().equals(instance.getLabel());
            }
        });

        pulseTransactionManager.runInTransaction(new Runnable()
        {
            public void run()
            {
                for (LabelConfiguration l: matchingLabels)
                {
                    LabelConfiguration copy = configurationTemplateManager.deepClone(l);
                    copy.setLabel(newName.getLabel());
                    configurationTemplateManager.save(copy);
                }
            }
        });
    }

    public void setConfigurationTemplateManager(ConfigurationTemplateManager configurationTemplateManager)
    {
        this.configurationTemplateManager = configurationTemplateManager;
    }

    public void setPulseTransactionManager(TransactionManager pulseTransactionManager)
    {
        this.pulseTransactionManager = pulseTransactionManager;
    }
}
