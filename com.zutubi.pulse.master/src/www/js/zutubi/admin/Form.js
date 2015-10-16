// dependency: ./namespace.js
// dependency: ./Button.js
// dependency: ./Checkbox.js
// dependency: ./ComboBox.js
// dependency: ./ControllingCheckbox.js
// dependency: ./DropDownList.js
// dependency: ./ItemPicker.js
// dependency: ./PasswordField.js
// dependency: ./StringList.js
// dependency: ./TextArea.js
// dependency: ./TextField.js

(function($)
{
    var ui = kendo.ui,
        Widget = ui.Widget,
        ns = ".kendoForm",
        CLICK = "click" + ns,
        KEYUP = "keyup" + ns,
        SELECTOR_FIELD_WRAPPER = ".k-field-wrapper",
        CREATED = "created",
        SUBMIT = "submit",
        DEFAULT_SUBMITS = ["apply", "reset"],
        FIELD_TYPES = {
            checkbox: "kendoZaCheckbox",
            "controlling-checkbox": "kendoZaControllingCheckbox",
            "controlling-select": "kendoZaControllingDropDownList",
            combobox: "kendoZaComboBox",
            dropdown: "kendoZaDropDownList",
            itempicker: "kendoZaItemPicker",
            password: "kendoZaPasswordField",
            stringlist: "kendoZaStringList",
            text: "kendoZaTextField",
            textarea: "kendoZaTextArea"
        };

    Zutubi.admin.Form = Widget.extend({
        init: function(element, options)
        {
            var that = this;

            Widget.fn.init.call(this, element, options);

            that._create();
        },

        events: [
            CREATED,
            SUBMIT
        ],

        options: {
            name: "ZaForm",
            formName: "form",
            template: '<form name="#: id #" id="#: id #"><table class="form"><tbody></tbody></table></form>',
            hiddenTemplate: '<input type="hidden" id="#: id #" name="#: name #">',
            fieldTemplate: '<tr><th><label id="#: id #-label" for="#: id #">#: label #</label></th><td><span id="#: id #-wrap" class="k-field-wrapper"></span></td></tr>',
            buttonTemplate: '<button id="#: id #" type="button" value="#: value #">#: name #</button>',
            errorTemplate: '<li>#: message #</li>'
        },

        _create: function()
        {
            var structure = this.options.structure,
                fields = structure.fields,
                submits = this.options.submits || DEFAULT_SUBMITS,
                fieldOptions,
                submitCell,
                i;

            this.id = "zaf-" + this.options.formName;

            this.fields = [];
            this.submits = [];
            this.template = kendo.template(this.options.template);
            this.hiddenTemplate = kendo.template(this.options.hiddenTemplate);
            this.fieldTemplate = kendo.template(this.options.fieldTemplate);
            this.buttonTemplate = kendo.template(this.options.buttonTemplate);
            this.errorTemplate = kendo.template(this.options.errorTemplate);

            this.element.html(this.template({id: this.id}));
            this.formElement = this.element.find("form");
            this.tableBodyElement = this.formElement.find("tbody");

            for (i = 0; i < fields.length; i++)
            {
                fieldOptions = fields[i];
                this._appendField(fieldOptions);
            }

            this.tableBodyElement.append('<tr><td class="submit" colspan="2"></td></tr>');
            submitCell = this.tableBodyElement.find(".submit");
            for (i = 0; i < submits.length; i++)
            {
                this._addSubmit(submits[i], submitCell);
            }

            if (this.options.values)
            {
                this.bindValues(this.options.values);
            }

            if (this.options.dirtyChecking)
            {
                this._updateButtons();
            }

            this.trigger(CREATED);
        },

        destroy: function()
        {
            var that = this;

            that.tableBodyElement.find(SELECTOR_FIELD_WRAPPER).off(ns);

            Widget.fn.destroy.call(that);
            kendo.destroy(that.element);

            that.element = null;
        },

        _appendField: function(fieldOptions)
        {
            var rowElement, fieldElement, fieldType, field;

            // HTML5 ids can contain most anything, but not spaces.  Our names can't include
            // slashes, so use them as a safe substitute.
            fieldOptions.id = this.id + "-" + fieldOptions.name.replace(/ /g, '/');

            if (fieldOptions.type === "hidden")
            {
                this.formElement.append(this.hiddenTemplate(fieldOptions));
            }
            else
            {
                rowElement = $(this.fieldTemplate(fieldOptions));
                fieldElement = rowElement.appendTo(this.tableBodyElement).find(SELECTOR_FIELD_WRAPPER);

                fieldType = FIELD_TYPES[fieldOptions.type];
                if (fieldType)
                {
                    field = fieldElement[fieldType]({
                        structure: fieldOptions,
                        parentForm: this
                    }).data(fieldType);

                    if (this.options.dirtyChecking)
                    {
                        fieldElement.on(KEYUP, jQuery.proxy(this._updateButtons, this));
                        fieldElement.on(CLICK, jQuery.proxy(this._updateButtons, this));
                        field.bind("change", jQuery.proxy(this._updateButtons, this));
                    }

                    this.fields.push(field);
                }
                else
                {
                    console.warn("Ignoring unsupported field type '" + fieldOptions.type + "'");
                }
            }
        },

        _addSubmit: function(name, parentElement)
        {
            var that = this,
                id = this.id + "-submit-" + name,
                element,
                button;

            parentElement.append(this.buttonTemplate({name: name, value: name, id: id}));
            element = parentElement.find("button").last();
            button = element.kendoZaButton({structure: {value: name}}).data("kendoZaButton");
            button.bind("click", function(e)
            {
                that._buttonClicked(e.sender.structure.value);
            });

            that.submits.push(button);
        },

        _buttonClicked: function(value)
        {
            if (value === "reset")
            {
                this.resetValues();
            }
            else
            {
                this.clearValidationErrors();
                this.trigger(SUBMIT, {value: value});
            }
        },

        _updateButtons: function()
        {
            var i,
                enabled = this.fields.length == 0 || this.isDirty();

            for (i = 0; i < this.submits.length; i++)
            {
                this.submits[i].enable(enabled);
            }
        },

        bindValues: function(values)
        {
            var i, field, name;

            if (typeof this.originalValues === "undefined")
            {
                this.originalValues = values;
            }

            for (i = 0; i < this.fields.length; i++)
            {
                field = this.fields[i];
                name = field.getFieldName();
                if (values.hasOwnProperty(name))
                {
                    field.bindValue(values[name]);
                }
            }

            this._updateButtons();
        },

        resetValues: function()
        {
            this.clearValidationErrors();
            if (this.originalValues)
            {
                this.bindValues(this.originalValues);
            }
        },

        getValues: function()
        {
            var values = {}, i, field;

            for (i = 0; i < this.fields.length; i++)
            {
                field = this.fields[i];
                values[field.getFieldName()] = field.getValue();
            }

            return values;
        },

        _isEmptyValue: function(value)
        {
            return value === null || value === "";
        },

        _arraysEqual: function(a1, a2)
        {
            var i;

            if (a1.length !== a2.length)
            {
                return false;
            }

            for (i = 0; i < a1.length; i++)
            {
                if (a1[i] !== a2[i])
                {
                    return false;
                }
            }

            return true;
        },

        _valuesEqual: function(v1, v2)
        {
            if (this._isEmptyValue(v1))
            {
                return this._isEmptyValue(v2);
            }

            if (Array.isArray(v1))
            {
                return Array.isArray(v2) && this._arraysEqual(v1, v2);
            }

            return String(v1) === String(v2);
        },

        isDirty: function()
        {
            var values, field, original, value;

            if (this.originalValues)
            {
                values = this.getValues();
                for (field in values)
                {
                    if (values.hasOwnProperty(field))
                    {
                        value = values[field];
                        if (this.originalValues.hasOwnProperty(field))
                        {
                            original = this.originalValues[field];
                            if (!this._valuesEqual(value, original))
                            {
                                return true;
                            }
                        }
                        else if (!this._isEmptyValue(value))
                        {
                            return true;
                        }
                    }
                }

                return false;
            }
            else
            {
                return true;
            }
        },

        getFields: function()
        {
            return this.fields;
        },

        getFieldNamed: function(name)
        {
            var i, field;

            for (i = 0; i < this.fields.length; i++)
            {
                field = this.fields[i];
                if (field.getFieldName() === name)
                {
                    return field;
                }
            }

            return null;
        },

        clearValidationErrors: function()
        {
            this.element.find(".validation-errors").remove();
        },

        showValidationErrors: function(errorDetails)
        {
            var field, fieldErrors;

            if (errorDetails.instanceErrors)
            {
                this._showInstanceErrors(errorDetails.instanceErrors);
            }

            fieldErrors = errorDetails.fieldErrors;
            if (fieldErrors)
            {
                for (field in fieldErrors)
                {
                    if (fieldErrors.hasOwnProperty(field))
                    {
                        this._showFieldErrors(field, fieldErrors[field]);
                    }
                }
            }
        },

        _showErrors: function(errorList, messages)
        {
            var i;

            for (i = 0; i < messages.length; i++)
            {
                errorList.append(this.errorTemplate({message: messages[i]}));
            }
        },

        _showInstanceErrors: function(messages)
        {
            var errorList = $('<ul class="validation-errors"></ul>').prependTo(this.element);
            this._showErrors(errorList, messages);
        },

        _showFieldErrors: function(fieldName, messages)
        {
            var field, fieldCell, errorList;

            if (messages.length)
            {
                field = this.getFieldNamed(fieldName);
                if (field)
                {
                    fieldCell = field.element.closest("td");
                    errorList = $('<ul class="validation-errors"></ul>').appendTo(fieldCell);
                    this._showErrors(errorList, messages);
                }
            }
        }
    });

    ui.plugin(Zutubi.admin.Form);
}(jQuery));
