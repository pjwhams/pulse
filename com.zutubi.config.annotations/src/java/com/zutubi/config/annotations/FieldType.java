package com.zutubi.config.annotations;

/**
 * <class-comment/>
 */
public interface FieldType
{
    /**
     * Text field type, represents a plain string value.
     */
    static final String TEXT = "text";

    /**
     * Email field is similar to a text field, but adds validation to ensure that the field only accepts
     * value emails.
     *
     * Note: This type is not yet supported.
     */
    static final String EMAIL = "email";

    /**
     *
     */
    static final String PASSWORD = "password";

    /**
     *
     */
    static final String HIDDEN = "hidden";

    /**
     * Note: This type is not yet supported.
     */
    static final String URL = "url";

    /**
     * Note: This type is not yet supported.
     */
    static final String FILE = "file";

    /**
     * Note: This type is not yet supported.
     */
    static final String DIRECTORY = "directory";

    /**
     * Note: This type is not yet supported.
     */
    static final String DATE = "date";

    /**
     * Note: This type is not yet supported.
     */
    static final String INTEGER = "int";

    static final String RADIO = "radio";

    static final String TEXTAREA = "textarea";

    static final String CONTROLLING_CHECKBOX = "controlling-checkbox";

    static final String CHECKBOX = "checkbox";

    static final String SELECT = "select";

    static final String ITEM_PICKER = "itempicker";
}
