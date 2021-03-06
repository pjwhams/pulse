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

package com.zutubi.pulse.core.scm.api;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.zutubi.util.io.FileSystemUtils;
import org.apache.tools.ant.types.selectors.SelectorUtils;

import java.io.File;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

/**
 * A predicate that is satisfied if and only if the tested string passes
 * specified inclusion and exclusion filters.  To pass the predicate, a path
 * must match at least one inclusion and no exclusions.  As a special case, if
 * there are no inclusions only exclusions are applied (i.e. specifying no
 * inclusions is equivalent to having inclusions that match all paths).
 *
 * The filter path format uses ant selector formatting.
 * <ul>
 * <li>** - matches multiple path components, ie: this/path/is/matched.</li>
 * <li>*  - matches a single path component.</li>
 * <li>the rest is matched literally.</li>
 * </ul>
 */
public class FilterPathsPredicate implements Predicate<String>
{
    private List<String> includedPaths = null;
    private List<String> excludedPaths = null;

    public FilterPathsPredicate(List<String> includedPaths, List<String> excludedPaths)
    {
        Function<String, String> normaliseFunction = new Function<String, String>()
        {
            public String apply(String s)
            {
                return normalisePath(s);
            }
        };

        this.includedPaths = newArrayList(transform(includedPaths, normaliseFunction));
        this.excludedPaths = newArrayList(transform(excludedPaths, normaliseFunction));
    }

    public boolean apply(String path)
    {
        path = normalisePath(path);
        return isIncluded(path) && !isExcluded(path);
    }

    private boolean isIncluded(String path)
    {
        return includedPaths.isEmpty() || pathMatchesPatterns(path, includedPaths);
    }

    private boolean isExcluded(String path)
    {
       return pathMatchesPatterns(path, excludedPaths);
    }

    private boolean pathMatchesPatterns(String path, List<String> patterns)
    {
        for (String pattern: patterns)
        {
            // The Ant selector will only match a path starting with the
            // file separator if the pattern also starts with the file
            // separator.  This is sensible enough in Ant context, but
            // here the SCMs can report paths like "/project/trunk/foo"
            // or "//depot/bar".  A user will probably expect **/... to
            // match in these cases, so we treat it as a special case.
            String matchPath = path;
            if (pattern.startsWith("**"))
            {
                while (matchPath.startsWith(File.separator))
                {
                    matchPath = matchPath.substring(1);
                }
            }

            if (SelectorUtils.matchPath(pattern, matchPath))
            {
                return true;
            }
        }

        return false;
    }

    private String normalisePath(String path)
    {
        return FileSystemUtils.localiseSeparators(path);
    }
}
