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

package com.zutubi.pulse.master.upgrade.tasks;

import com.zutubi.tove.type.record.MutableRecord;

/**
 * Deletes an existing meta property from a record.
 */
class DeleteMetaPropertyRecordUpgrader implements RecordUpgrader
{
    private String name;

    /**
     * @param name the name of the meta property to delete
     */
    public DeleteMetaPropertyRecordUpgrader(String name)
    {
        this.name = name;
    }

    public void upgrade(String path, MutableRecord record)
    {
        record.removeMeta(name);
    }
}
