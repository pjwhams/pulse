package com.zutubi.pulse.master.rest.model.forms;

import com.zutubi.tove.annotations.FieldType;

/**
 * Models a checkbox that controls the enabled/disabled state of other fields in a form.
 */
public class ControllingCheckboxFieldModel extends FieldModel
{
    private String[] checkedFields;
    private String[] uncheckedFields;

    public ControllingCheckboxFieldModel()
    {
        setType(FieldType.CONTROLLING_CHECKBOX);
        setSubmitOnEnter(true);
    }

    public String[] getCheckedFields()
    {
        return checkedFields;
    }

    public void setCheckedFields(String[] checkedFields)
    {
        this.checkedFields = checkedFields;
    }

    public String[] getUncheckedFields()
    {
        return uncheckedFields;
    }

    public void setUncheckedFields(String[] uncheckedFields)
    {
        this.uncheckedFields = uncheckedFields;
    }
}
