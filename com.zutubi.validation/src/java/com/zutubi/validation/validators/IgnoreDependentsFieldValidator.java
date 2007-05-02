package com.zutubi.validation.validators;

import com.zutubi.validation.ValidationException;

/**
 * A validator that inspects the value of a field and decides based on this
 * if some dependent fields should be ignored for validation purposes.
 */
public class IgnoreDependentsFieldValidator extends FieldValidatorSupport
{
    private String nonIgnoreValue;
    private String[] dependentFields;

    public void setNonIgnoreValue(String nonIgnoreValue)
    {
        this.nonIgnoreValue = nonIgnoreValue;
    }

    public void setDependentFields(String[] dependentFields)
    {
        this.dependentFields = dependentFields;
    }

    public void validate(Object obj) throws ValidationException
    {
        Object value = getFieldValue(getFieldName(), obj);
        boolean equal;
        if(value == null)
        {
            equal = nonIgnoreValue == null;
        }
        else
        {
            equal = value.toString().equals(nonIgnoreValue);
        }

        if(!equal)
        {
            for(String dependentField: dependentFields)
            {
                getValidationContext().addIgnoredField(dependentField);
            }
        }
    }
}
