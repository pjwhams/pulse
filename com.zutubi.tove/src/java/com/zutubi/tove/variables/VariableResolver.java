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

package com.zutubi.tove.variables;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import com.zutubi.tove.variables.api.ResolutionException;
import com.zutubi.tove.variables.api.Variable;
import com.zutubi.tove.variables.api.VariableMap;
import com.zutubi.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Methods for analysing and replacing variables within strings.
 */
public class VariableResolver
{
    /**
     * Set of all characters that are reserved for special meanings in the
     * extended reference syntax $(...).
     */
    public static Set<Character> EXTENDED_SPECIAL_CHARS = Sets.newHashSet(')', '?', '|', '!', '%', '#', '&', '/', ':', ';');

    private static final Map<String, Function<String, String>> FILTER_FUNCTIONS = new HashMap<String, Function<String, String>>();

    static
    {
        FILTER_FUNCTIONS.put("trim", StringUtils.trim());

        FILTER_FUNCTIONS.put("lower", new Function<String, String>()
        {
            public String apply(String s)
            {
                return s.toLowerCase();
            }
        });

        FILTER_FUNCTIONS.put("upper", new Function<String, String>()
        {
            public String apply(String s)
            {
                return s.toUpperCase();
            }
        });

        FILTER_FUNCTIONS.put("name", new Function<String, String>()
        {
            public String apply(String s)
            {
                return s.trim().replaceAll("[\\\\/$]", ".");
            }
        });

        FILTER_FUNCTIONS.put("normalise", new Function<String, String>()
        {
            public String apply(String s)
            {
                return s.trim().replaceAll("[\\\\/]", Matcher.quoteReplacement(File.separator));
            }
        });

        FILTER_FUNCTIONS.put("normalize", FILTER_FUNCTIONS.get("normalise"));
    }

    public enum ResolutionStrategy
    {
        /**
         * Resolve all variables, throwing an error for non-existant variables.
         */
        RESOLVE_STRICT(true),
        /**
         * Try to resolve all variables but leave non-existant variables as
         * they are.
         */
        RESOLVE_NON_STRICT(true);

        private boolean resolve;

        private ResolutionStrategy(boolean resolve)
        {
            this.resolve = resolve;
        }

        public boolean resolve()
        {
            return resolve;
        }
    }

    /**
     * Tokens identified by lexer.
     */
    private enum TokenType
    {
        SPACE,
        TEXT,
        VARIABLE,
        DEFAULT_VALUE,
        FILTER
    }

    /**
     * The lexer is a hand-written state machine with the below states (plus
     * modifiers).
     */
    private enum LexerState
    {
        INITIAL,
        ESCAPED,
        DOLLAR,
        VARIABLE_NAME,
        EXTENDED_VARIABLE_NAME,
        DEFAULT_VALUE,
        FILTER_NAME
    }

    /**
     * A token produced by the lexer.
     */
    private static class Token
    {
        public TokenType type;
        public String value;

        public Token(TokenType type, String value)
        {
            this.type = type;
            this.value = value;
        }
    }

    private static boolean escapable(char c, boolean split)
    {
        switch (c)
        {
            case '$':
            case '\\':
                return true;
            case '"':
            case ' ':
                return split;
            default:
                return false;
        }
    }

    private static List<Token> tokenise(String input, boolean split) throws ResolutionException
    {
        List<Token> result = new LinkedList<Token>();
        LexerState state = LexerState.INITIAL;
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean haveData = false;

        for (int i = 0; i < input.length(); i++)
        {
            char inputChar = input.charAt(i);

            switch (state)
            {
                case INITIAL:
                {
                    switch (inputChar)
                    {
                        case '\\':
                        {
                            state = LexerState.ESCAPED;
                            break;
                        }
                        case '"':
                        {
                            if (split)
                            {
                                if (quoted)
                                {
                                    quoted = false;
                                }
                                else
                                {
                                    quoted = true;
                                    haveData = true;
                                }
                            }
                            else
                            {
                                current.append(inputChar);
                                haveData = true;
                            }
                            break;
                        }
                        case ' ':
                        {
                            if (split)
                            {
                                if (quoted)
                                {
                                    current.append(inputChar);
                                    haveData = true;
                                }
                                else
                                {
                                    addCurrent(current, haveData, result);
                                    haveData = false;
                                    result.add(new Token(TokenType.SPACE, " "));
                                }
                            }
                            else
                            {
                                current.append(inputChar);
                                haveData = true;
                            }
                            break;
                        }
                        case '$':
                        {
                            state = LexerState.DOLLAR;
                            // only add a token if there is something to add.
                            addCurrent(current, haveData, result);
                            haveData = false;
                            break;
                        }
                        default:
                        {
                            current.append(inputChar);
                            haveData = true;
                            break;
                        }
                    }
                    break;
                }
                case ESCAPED:
                {
                    if (!escapable(inputChar, split))
                    {
                        current.append('\\');
                    }

                    current.append(inputChar);
                    haveData = true;
                    state = LexerState.INITIAL;
                    break;
                }
                case DOLLAR:
                {
                    switch (inputChar)
                    {
                        case '{':
                        {
                            state = LexerState.VARIABLE_NAME;
                            break;
                        }
                        case '(':
                        {
                            state = LexerState.EXTENDED_VARIABLE_NAME;
                            break;
                        }
                        default:
                        {
                            throw new ResolutionException("Syntax error: expecting '{' or '(', got '" + inputChar + "'");
                        }
                    }
                    break;
                }
                case VARIABLE_NAME:
                {
                    switch (inputChar)
                    {
                        case '}':
                        {
                            if (current.length() == 0)
                            {
                                throw new ResolutionException("Syntax error: empty variable");
                            }

                            result.add(new Token(TokenType.VARIABLE, current.toString()));
                            state = LexerState.INITIAL;
                            current.delete(0, current.length());
                            break;
                        }
                        default:
                        {
                            current.append(inputChar);
                            break;
                        }
                    }
                    break;
                }
                case EXTENDED_VARIABLE_NAME:
                {
                    switch (inputChar)
                    {
                        case ')':
                        case '?':
                        case '|':
                        {
                            if (current.length() == 0)
                            {
                                throw new ResolutionException("Syntax error: empty variable");
                            }

                            result.add(new Token(TokenType.VARIABLE, current.toString()));
                            current.delete(0, current.length());
                            state = chooseExtendedState(inputChar);
                            break;
                        }
                        case '!':
                        case '%':
                        case '#':
                        case '&':
                        case '/':
                        case ':':
                        case ';':
                        {
                            throw new ResolutionException("Syntax error: '" + inputChar + "' is reserved and may not be used in an extended variable name");
                        }
                        default:
                        {
                            current.append(inputChar);
                            break;
                        }
                    }
                    break;
                }
                case DEFAULT_VALUE:
                {
                    switch (inputChar)
                    {
                        case ')':
                        {
                            result.add(new Token(TokenType.DEFAULT_VALUE, current.toString()));
                            state = LexerState.INITIAL;
                            current.delete(0, current.length());
                            break;
                        }
                        default:
                        {
                            current.append(inputChar);
                            break;
                        }
                    }
                    break;
                }
                case FILTER_NAME:
                {
                    switch (inputChar)
                    {
                        case '?':
                        case ')':
                        case '|':
                        {
                            result.add(new Token(TokenType.FILTER, current.toString()));
                            current.delete(0, current.length());
                            state = chooseExtendedState(inputChar);
                            break;
                        }
                        default:
                        {
                            current.append(inputChar);
                            break;
                        }
                    }
                    break;
                }
            }
        }

        switch (state)
        {
            case INITIAL:
            {
                if (quoted)
                {
                    throw new ResolutionException("Syntax error: unexpected end of input looking for closing quotes (\")");
                }

                addCurrent(current, haveData, result);
                break;
            }
            case ESCAPED:
            {
                current.append('\\');
                haveData = true;
                addCurrent(current, haveData, result);
                break;
            }
            case DOLLAR:
            {
                throw new ResolutionException("Syntax error: unexpected end of input looking for '{' or '('");
            }
            case VARIABLE_NAME:
            {
                throw new ResolutionException("Syntax error: unexpected end of input looking for '}'");
            }
            case EXTENDED_VARIABLE_NAME:
            case DEFAULT_VALUE:
            case FILTER_NAME:
            {
                throw new ResolutionException("Syntax error: unexpected end of input looking for ')'");
            }
        }

        return result;
    }

    private static LexerState chooseExtendedState(char inputChar)
    {
        switch (inputChar)
        {
            case ')':
            {
                return LexerState.INITIAL;
            }
            case '|':
            {
                return LexerState.FILTER_NAME;
            }
            default:
            {
                return LexerState.DEFAULT_VALUE;
            }
        }
    }

    private static void addCurrent(StringBuilder current, boolean haveData, List<Token> result)
    {
        if (haveData)
        {
            result.add(new Token(TokenType.TEXT, current.toString()));
            current.delete(0, current.length());
        }
    }

    private enum ParseElementType
    {
        TEXT,
        SPACE,
        VARIABLE
    }

    /**
     * Base for parse elements: we don't use a full-blown tree, we just compose
     * related tokens into single elements of specific types.
     */
    private static abstract class ParseElement
    {
        private ParseElementType type;

        protected ParseElement(ParseElementType type)
        {
            this.type = type;
        }
    }

    private static class SimpleElement extends ParseElement
    {
        private String value;

        private SimpleElement(ParseElementType type, String value)
        {
            super(type);
            this.value = value;
        }
    }

    private static class VariableElement extends ParseElement
    {
        private String name;
        private List<String> filters = new LinkedList<String>();
        private String defaultValue;

        private VariableElement(ParseElementType type, String name)
        {
            super(type);
            this.name = name;
        }

        public List<String> getFilters()
        {
            return filters;
        }

        public void addFilter(String name)
        {
            filters.add(name);
        }
    }

    private static List<ParseElement> parse(String input, boolean split) throws ResolutionException
    {
        List<ParseElement> parseElements = new LinkedList<ParseElement>();
        List<Token> tokens = tokenise(input, split);
        for (Token token : tokens)
        {
            switch (token.type)
            {
                case TEXT:
                {
                    parseElements.add(new SimpleElement(ParseElementType.TEXT, token.value));
                    break;
                }
                case SPACE:
                {
                    parseElements.add(new SimpleElement(ParseElementType.SPACE, token.value));
                    break;
                }
                case VARIABLE:
                {
                    parseElements.add(new VariableElement(ParseElementType.VARIABLE, token.value));
                    break;
                }
                case DEFAULT_VALUE:
                {
                    VariableElement variable = (VariableElement) parseElements.get(parseElements.size() - 1);
                    variable.defaultValue = token.value;
                    break;
                }
                case FILTER:
                {
                    VariableElement variable = (VariableElement) parseElements.get(parseElements.size() - 1);
                    variable.addFilter(token.value);
                    break;
                }
            }
        }

        return parseElements;
    }

    public static boolean containsVariable(String input) throws ResolutionException
    {
        List<Token> tokens = tokenise(input, false);
        for (Token token : tokens)
        {
            switch (token.type)
            {
                case VARIABLE:
                    return true;
            }
        }
        return false;
    }

    public static Object resolveVariable(String input, VariableMap variables, ResolutionStrategy resolutionStrategy) throws ResolutionException
    {
        List<ParseElement> elements = parse(input, false);
        if (elements.size() != 1 || elements.get(0).type != ParseElementType.VARIABLE)
        {
            throw new ResolutionException("Expected single variable. Instead found '" + input + "'");
        }
        VariableElement element = (VariableElement) elements.get(0);
        Variable ref = variables.getVariable(element.name);
        if (ref != null)
        {
            return ref.getValue();
        }
        else if (element.defaultValue != null)
        {
            return element.defaultValue;
        }
        else if (resolutionStrategy == ResolutionStrategy.RESOLVE_STRICT)
        {
            throw new ResolutionException("Unknown variable '" + element.name + "'");
        }
        else
        {
            return null;
        }
    }

    public static String resolveVariables(String input, VariableMap variables) throws ResolutionException
    {
        return resolveVariables(input, variables, ResolutionStrategy.RESOLVE_STRICT);
    }

    public static String resolveVariables(String input, VariableMap variables, ResolutionStrategy resolutionStrategy) throws ResolutionException
    {
        StringBuilder result = new StringBuilder();

        List<ParseElement> elements = parse(input, false);
        for (ParseElement element : elements)
        {
            switch (element.type)
            {
                case TEXT:
                {
                    result.append(((SimpleElement) element).value);
                    break;
                }
                case VARIABLE:
                {
                    result.append(resolveVariable(variables, (VariableElement) element, resolutionStrategy));
                    break;
                }
            }
        }
        return result.toString();
    }

    /**
     * Resolves variables in input non-strictly (variables that are not recognised are left
     * unresolved), returning the original input if any syntax error is found.
     * 
     * @param input     the input to be resolved
     * @param variables a set of variables to use for resolution
     * @return the input with as many variables resolved as possible
     */
    public static String safeResolveVariables(String input, VariableMap variables)
    {
        try
        {
            return resolveVariables(input, variables, ResolutionStrategy.RESOLVE_NON_STRICT);
        }
        catch (ResolutionException e)
        {
            return input;
        }
    }

    public static List<String> splitAndResolveVariable(String input, VariableMap variables, ResolutionStrategy resolutionStrategy) throws ResolutionException
    {
        List<String> result = new LinkedList<String>();
        StringBuilder current = new StringBuilder();
        boolean haveData = false;

        List<ParseElement> elements = parse(input, true);

        for (ParseElement element : elements)
        {
            switch (element.type)
            {
                case SPACE:
                {
                    if (haveData)
                    {
                        result.add(current.toString());
                        current.delete(0, current.length());
                        haveData = false;
                    }
                    break;
                }
                case TEXT:
                {
                    current.append(((SimpleElement) element).value);
                    haveData = true;
                    break;
                }
                case VARIABLE:
                {
                    String value = resolveVariable(variables, (VariableElement) element, resolutionStrategy);
                    if (value.length() > 0)
                    {
                        current.append(value);
                        haveData = true;
                    }
                    break;
                }
            }
        }

        if (haveData)
        {
            result.add(current.toString());
        }

        return result;
    }

    private static String resolveVariable(VariableMap variables, VariableElement element, ResolutionStrategy resolutionStrategy) throws ResolutionException
    {
        if (resolutionStrategy.resolve())
        {
            Variable variable = variables.getVariable(element.name);
            if (variable != null && variable.getValue() != null)
            {
                return filter(variable.getValue().toString(), element.getFilters(), resolutionStrategy);
            }
            else if (element.defaultValue != null)
            {
                return element.defaultValue;
            }
            else if (resolutionStrategy == ResolutionStrategy.RESOLVE_STRICT)
            {
                throw new ResolutionException("Unknown variable '" + element.name + "'");
            }
        }

        return "$(" + element.name + ")";
    }

    private static String filter(String value, List<String> filters, ResolutionStrategy resolutionStrategy) throws ResolutionException
    {
        for (String filter : filters)
        {
            Function<String, String> fn = FILTER_FUNCTIONS.get(filter);
            if (fn == null)
            {
                if (resolutionStrategy == ResolutionStrategy.RESOLVE_STRICT)
                {
                    throw new ResolutionException("Unknown filter '" + filter + "'");
                }
            }
            else
            {
                value = fn.apply(value);
            }
        }

        return value;
    }
}
