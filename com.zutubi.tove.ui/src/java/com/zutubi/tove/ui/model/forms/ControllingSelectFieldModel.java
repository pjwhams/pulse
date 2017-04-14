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

package com.zutubi.tove.ui.model.forms;

import com.zutubi.tove.annotations.FieldType;

/**
 * Describes a controlling select: i.e. a select whose selected value controls the enabled state of
 * other fields.
 */
public class ControllingSelectFieldModel extends OptionFieldModel
{
    private String[] enableSet;
    private String[] dependentFields;

    public ControllingSelectFieldModel()
    {
        setType(FieldType.CONTROLLING_SELECT);
    }

    public String[] getEnableSet()
    {
        return enableSet;
    }

    public void setEnableSet(String[] enableSet)
    {
        this.enableSet = enableSet;
    }

    public String[] getDependentFields()
    {
        return dependentFields;
    }

    public void setDependentFields(String[] dependentFields)
    {
        this.dependentFields = dependentFields;
    }

    protected boolean transformType()
    {
        return false;
    }
}