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

package com.zutubi.util;

/**
 * A utilities class for working with enums.
 */
public class EnumUtils
{
    /**
     * Convert the enumeration into a human readable equivalent, replacing '_' with ' '
     * and lowercasing.
     *
     * @param e the enum to be converted.
     * @return  the converted string.
     *
     * @see #toPrettyString(String)
     */
    public static String toPrettyString(Enum e)
    {
        return toPrettyString(e.name());
    }

    /**
     * Convert the name of an enumeration into a human readable equivalent, replacing '_' with ' ' 
     * and lowercasing.
     *
     * @param name  the name of the enum to be converted.
     * @return  the converted string.
     *
     * @see #toPrettyString(Enum)
     */
    public static String toPrettyString(String name)
    {
        return name.replace('_', ' ').toLowerCase();
    }

    /**
     * Convert the enum into a machine readable equivalent, replacing '_' with '' and lowercasing.
     *
     * @param e the enum to be converted
     * @return  the converted string
     */
    public static String toString(Enum e)
    {
        return toString(e.name());
    }

    /**
     * Converts the enums name into a machine readable equivalent, replacing '_' with '' and lowercasing.
     *
     * @param name  the name of the enum to be converted
     * @return  the converted string
     */
    public static String toString(String name)
    {
        return name.replace("_", "").toLowerCase();
    }

    /**
     * Converts a pretty string version of an enumeration's name into its name so that the
     * enumeration instance can be retrieved via {@link java.lang.Enum#valueOf(Class, String)}
     * 
     * @param prettyString  the pretty string form of the enumerations name
     * @return  the enumerations name.
     *
     * @see #toPrettyString(String)
     */
    public static String fromPrettyString(String prettyString)
    {
        return prettyString.replace(' ', '_').toUpperCase();
    }

    /**
     * Converts a pretty string version of an enumeration's name into the enumeration instance.
     *
     * @param type          type of the enumeration to convert to
     * @param prettyString  the pretty string form of the enumerations name
     * @return  the enumeration instance
     *
     * @see #toPrettyString(Enum)
     */
    public static <V extends Enum<V>> V fromPrettyString(Class<V> type, String prettyString)
    {
        return Enum.valueOf(type, fromPrettyString(prettyString));
    }
}
