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

package com.zutubi.tove.config.docs;

import com.google.common.collect.ImmutableMap;
import com.zutubi.i18n.Messages;
import com.zutubi.tove.annotations.Form;
import com.zutubi.tove.type.CompositeType;
import com.zutubi.tove.type.TypeProperty;
import com.zutubi.util.StringUtils;
import com.zutubi.util.bean.BeanException;
import com.zutubi.util.bean.BeanUtils;
import com.zutubi.util.logging.Logger;

import java.util.*;

/**
 * A repository for documentation of configuration types.  The information is
 * generated once per type and cached here for all future requests.  It is
 * provided in data form so that it can be munged into a suitable format
 * depending on where it is requested from.  For example, it can be presented
 * on a web page or return via a programmable API.
 */
public class ConfigurationDocsManager
{
    private static final Logger LOG = Logger.getLogger(ConfigurationDocsManager.class);

    private static final Map<String, String> TYPE_KEY_MAP = ImmutableMap.of("form.heading", "title",
                                                                            "label", "title",
                                                                            "introduction", "brief",
                                                                            "verbose", "verbose");


    private static final Map<String, String> PROPERTY_KEY_MAP = ImmutableMap.of("label", "label",
                                                                                      "help", "brief",
                                                                                      "verbose", "verbose");

    private static final Map<String, String> EXAMPLE_KEY_MAP = ImmutableMap.of("blurb", "blurb");

    private static final int TRIM_LIMIT = 100;

    private Map<String, TypeDocs> cache = new HashMap<String, TypeDocs>();

    public synchronized TypeDocs getDocs(CompositeType type)
    {
        if (useCache())
        {
            TypeDocs result = cache.get(type.getSymbolicName());
            if (result == null)
            {
                result = generateDocs(type);
                cache.put(type.getSymbolicName(), result);
            }

            return result;
        }
        else
        {
            return generateDocs(type);
        }
    }

    private boolean useCache()
    {
        return !Boolean.getBoolean("com.zutubi.tove.docs.disable.cache");
    }

    private TypeDocs generateDocs(CompositeType type)
    {
        TypeDocs typeDocs = new TypeDocs();
        Messages messages = Messages.getInstance(type.getClazz());

        setDetails(messages, typeDocs, "", TYPE_KEY_MAP);
        ensureBrief(typeDocs);

        // Deliberately match the order to the Form, so no post-sorting is required.
        List<String> orderedFields = new ArrayList<>();
        Form form = type.getAnnotation(Form.class, true);
        if (form != null)
        {
            orderedFields.addAll(Arrays.asList(form.fieldOrder()));
        }

        for (String fieldName: type.getSimplePropertyNames())
        {
            if (!orderedFields.contains(fieldName))
            {
                orderedFields.add(fieldName);
            }
        }

        for (String fieldName : orderedFields)
        {
            TypeProperty property = type.getProperty(fieldName);
            if (property != null)
            {
                PropertyDocs propertyDocs = new PropertyDocs(property.getName());
                setDetails(messages, propertyDocs, property.getName() + ".", PROPERTY_KEY_MAP);
                findExamples(messages, propertyDocs, property);
                typeDocs.addProperty(propertyDocs);
            }
        }

        return typeDocs;
    }

    private void findExamples(Messages messages, PropertyDocs propertyDocs, TypeProperty property)
    {
        for(int i = 1; /* forever */; i++)
        {
            String exampleKey = String.format("%s.example.%d", property.getName(), i);
            if(messages.isKeyDefined(exampleKey))
            {
                Example example = new Example(messages.format(exampleKey));
                setDetails(messages, example, exampleKey + ".", EXAMPLE_KEY_MAP);
                propertyDocs.addExample(example);
            }
            else
            {
                break;
            }
        }
    }

    private void ensureBrief(Docs docs)
    {
        if(!StringUtils.stringSet(docs.getBrief()) && StringUtils.stringSet(docs.getVerbose()))
        {
            docs.setBrief(StringUtils.trimmedString(stripTags(docs.getVerbose()), TRIM_LIMIT));
        }
    }

    private String stripTags(String s)
    {
        // Lazily create as no tags is a common case.
        StringBuilder builder = null;
        boolean inTag = false;
        int fragmentStart = 0;

        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            if (inTag)
            {
                if (c == '>')
                {
                    inTag = false;
                    fragmentStart = i + 1;
                }
            }
            else
            {
                if (c == '<')
                {
                    if (builder == null)
                    {
                        builder = new StringBuilder(s.length());
                    }

                    if (fragmentStart < i)
                    {
                        appendFragment(s.substring(fragmentStart, i), builder);
                    }

                    inTag = true;
                }
            }
        }

        if (builder == null)
        {
            return s;
        }
        else
        {
            if (fragmentStart < s.length())
            {
                builder.append(s.substring(fragmentStart, s.length()));
            }

            return builder.toString();
        }
    }

    private void appendFragment(String fragment, StringBuilder builder)
    {
        if (builder.length() > 0 &&
                !Character.isWhitespace(builder.charAt(builder.length() - 1)) &&
                !Character.isWhitespace(fragment.charAt(0)))
        {
            builder.append(' ');
        }

        builder.append(fragment);
    }

    private void setDetails(Messages messages, Object docs, String prefix, Map<String, String> keyMap)
    {
        for(Map.Entry<String, String> entry: keyMap.entrySet())
        {
            setPropertyIfDefined(messages, prefix + entry.getKey(), docs, entry.getValue());
        }
    }

    private void setPropertyIfDefined(Messages messages, String key, Object target, String property)
    {
        try
        {
            String current = (String) BeanUtils.getProperty(property, target);
            if(!StringUtils.stringSet(current) && messages.isKeyDefined(key))
            {
                BeanUtils.setProperty(property, messages.format(key), target);
            }
        }
        catch (BeanException e)
        {
            LOG.warning(e);
        }
    }
}
