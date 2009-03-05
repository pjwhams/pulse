package com.zutubi.pulse.servercore.jetty;

import com.zutubi.util.TextUtils;

import java.beans.PropertyEditorSupport;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.List;
import java.util.LinkedList;

/**
 * A text property converter to allow easy configuration of the authority definitions
 * within a spring context file.
 */
public class AuthorityDefinitionsEditor  extends PropertyEditorSupport
{
    public void setAsText(String text) throws IllegalArgumentException
    {
        AuthorityDefinitions instance = new AuthorityDefinitions();
        try
        {
            text = text.trim();
            BufferedReader reader = new BufferedReader(new StringReader(text));
            String line;
            while (TextUtils.stringSet(line = reader.readLine()))
            {
                StringTokenizer tokens = new StringTokenizer(line, ", ", false);
                String path = tokens.nextToken();
                String role = tokens.nextToken();
                List<String> methods = new LinkedList<String>();
                while (tokens.hasMoreTokens())
                {
                    methods.add(tokens.nextToken());
                }
                instance.addPrivilege(path, role, methods.toArray(new String[methods.size()]));
            }
        }
        catch (IOException e)
        {
            // this will not happen.
        }

        setValue(instance);
    }
}
