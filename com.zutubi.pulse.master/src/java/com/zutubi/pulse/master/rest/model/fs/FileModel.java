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

package com.zutubi.pulse.master.rest.model.fs;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a file in a hierarchical file system.
 */
public class FileModel
{
    private String name;
    private boolean directory;
    private List<FileModel> nested;

    public FileModel(String name, boolean directory)
    {
        this.name = name;
        this.directory = directory;
    }

    public String getName()
    {
        return name;
    }

    public boolean isDirectory()
    {
        return directory;
    }

    public List<FileModel> getNested()
    {
        return nested;
    }

    public void addNested(FileModel f)
    {
        if (nested == null)
        {
            nested = new ArrayList<>();
        }

        nested.add(f);
    }
}
