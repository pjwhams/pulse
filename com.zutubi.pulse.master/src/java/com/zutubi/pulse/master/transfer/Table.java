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

package com.zutubi.pulse.master.transfer;

import java.util.List;

/**
 *
 *
 */
public interface Table
{
    /**
     * The name of the table being transfered.
     *
     * @return name of the table.
     */
    String getName();

    /**
     * Get the list of columns associated with this table.
     *
     * @return a list of columns.
     */
    List<Column> getColumns();

    /**
     * Get the named column associated with this table.
     * @param name of the column being requested.
     * @return the column instance, or null if that column does not exist.
     */
    Column getColumn(String name);
}
