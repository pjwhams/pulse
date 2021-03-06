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

package com.zutubi.tove.ui.wizards;

import com.zutubi.i18n.Messages;
import com.zutubi.tove.config.ConfigurationReferenceManager;
import com.zutubi.tove.config.ConfigurationTemplateManager;
import com.zutubi.tove.config.api.Configuration;
import com.zutubi.tove.type.CompositeType;
import com.zutubi.tove.type.SimpleInstantiator;
import com.zutubi.tove.type.TypeException;
import com.zutubi.tove.type.TypeRegistry;
import com.zutubi.tove.type.record.MutableRecord;
import com.zutubi.tove.type.record.TemplateRecord;
import com.zutubi.tove.ui.ConfigModelBuilder;
import com.zutubi.tove.ui.ToveUiUtils;
import com.zutubi.tove.ui.ValidationException;
import com.zutubi.tove.ui.forms.FormContext;
import com.zutubi.tove.ui.model.CompositeModel;
import com.zutubi.tove.ui.model.CompositeTypeModel;
import com.zutubi.tove.ui.model.TypedWizardStepModel;
import com.zutubi.tove.ui.model.WizardTypeModel;
import com.zutubi.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A service that helps create and process wizard steps for types.
 */
public class WizardModelBuilder
{
    private static final String KEY_WIZARD_LABEL = "wizard.label";
    private static final String KEY_WIZARD_HELP = "wizard.help";

    private ConfigurationTemplateManager configurationTemplateManager;
    private ConfigurationReferenceManager configurationReferenceManager;
    private ConfigModelBuilder configModelBuilder;
    private TypeRegistry typeRegistry;

    public TypedWizardStepModel buildStepForClass(String key, Class<? extends Configuration> clazz, FormContext context)
    {
        return buildStepForType(key, getCompositeType(clazz), context);
    }

    public TypedWizardStepModel buildStepForType(String key, CompositeType type, FormContext context)
    {
        List<CompositeType> types;
        if (type.isExtendable())
        {
            types = type.getExtensions();
        }
        else
        {
            types = Collections.singletonList(type);
        }

        Messages messages = Messages.getInstance(type.getClazz());
        TypedWizardStepModel step = new TypedWizardStepModel(key, messages.format("label"));
        for (CompositeType stepType : types)
        {
            messages = Messages.getInstance(stepType.getClazz());
            String labelKey = messages.isKeyDefined(KEY_WIZARD_LABEL) ? "wizard.label" : "label";
            WizardTypeModel typeModel = new WizardTypeModel(configModelBuilder.buildCompositeTypeModel(stepType, context, null), messages.format(labelKey));
            step.addType(typeModel);
            if (messages.isKeyDefined(KEY_WIZARD_HELP))
            {
                typeModel.setHelp(messages.format(KEY_WIZARD_HELP));
            }
        }
        return step;
    }

    public CompositeType typeCheck(Map<String, CompositeModel> models, String key, Class<? extends Configuration> expectedClazz)
    {
        return typeCheck(models, key, getCompositeType(expectedClazz));
    }

    /**
     * Checks that an expected model is both provided and compatible with its expected type.
     *
     * @param models all provided models
     * @param key key of the expected model to check
     * @param expectedType type the model must be compatible with
     * @return the actual type of the model (may be an extension of the expected type)
     * @throws IllegalArgumentException if the model is not found or has an unknown or incompatible type
     */
    public CompositeType typeCheck(Map<String, CompositeModel> models, String key, CompositeType expectedType)
    {
        CompositeModel model = models.get(key);
        if (model == null)
        {
            throw new IllegalArgumentException("A model with key '" + key + "' is required");
        }

        CompositeType actualType = null;
        CompositeType postedType = null;
        CompositeTypeModel typeModel = model.getType();

        if (expectedType.isExtendable())
        {
            if (expectedType.getExtensions().size() == 1)
            {
                actualType = expectedType.getExtensions().get(0);
            }
        }
        else
        {
            actualType = expectedType;
        }

        if (typeModel != null && typeModel.getSymbolicName() != null)
        {
            postedType = typeRegistry.getType(typeModel.getSymbolicName());
            if (postedType == null)
            {
                throw new IllegalArgumentException("Model for key '" + key + "' has type with unknown symbolic name '" + typeModel.getSymbolicName() + "'");
            }
        }

        if (actualType == null)
        {
            if (postedType == null)
            {
                throw new IllegalArgumentException("Model for key '" + key + "' requires a type with symbolic name.");
            }

            actualType = postedType;
        }

        if (!expectedType.isAssignableFrom(actualType))
        {
            throw new IllegalArgumentException("Model for key '" + key + "' has incompatible type '" + actualType.getSymbolicName() + "'.");
        }

        return actualType;
    }

    public MutableRecord buildRecord(TemplateRecord templateParentRecord, String templateOwnerPath, CompositeType type, CompositeModel model) throws TypeException
    {
        MutableRecord record;
        if (templateParentRecord == null)
        {
            record = ToveUiUtils.convertProperties(type, templateOwnerPath, model.getProperties());
        }
        else
        {
            // Start with the record we would get by inheriting and adding nothing (note this will exclude e.g.
            // non-inheritable values), then apply the values provided by the wizard, then fill in any empty values with
            // defaults where available.
            TemplateRecord trivialChild = new TemplateRecord("", templateParentRecord, type, type.createNewRecord(false));
            record = trivialChild.flatten(false);
            MutableRecord providedRecord = ToveUiUtils.convertProperties(type, templateOwnerPath, model.getProperties());
            // CIB-3046: If the user has based their provided record off the parent, it may include suppressed
            // passwords.  So replace any suppressions with values from the parent.
            ToveUiUtils.unsuppressPasswords(record, providedRecord, type, false);
            record.update(providedRecord, false, true);
            record.update(type.createNewRecord(true), false, false);
        }

        return record;
    }

    public MutableRecord buildAndValidateRecord(CompositeType type, String key, WizardContext context) throws TypeException
    {
        CompositeType actualType = typeCheck(context.getModels(), key, type);
        TemplateRecord templateParentRecord = null;
        if (context.getTemplateParentRecord() != null)
        {
            if (StringUtils.stringSet(key))
            {
                templateParentRecord = (TemplateRecord) context.getTemplateParentRecord().get(key);
            }
            else
            {
                templateParentRecord = context.getTemplateParentRecord();
            }
        }

        MutableRecord record = buildRecord(templateParentRecord, context.getTemplateOwnerPath(), actualType, context.getModels().get(key));
        Configuration instance = configurationTemplateManager.validate(context.getParentPath(), null, record, context.isConcrete(), false);
        if (!instance.isValid())
        {
            throw new ValidationException(instance, key);
        }
        return record;
    }

    public Configuration buildAndValidateTransientInstance(CompositeType type, String parentPath, String baseName, MutableRecord record) throws TypeException
    {
        SimpleInstantiator instantiator = new SimpleInstantiator(null, configurationReferenceManager, configurationTemplateManager);
        Configuration instance = (Configuration) instantiator.instantiate(type, record);
        try
        {
            configurationTemplateManager.validateInstance(getCompositeType(instance.getClass()), instance, parentPath, baseName, true, true, false, null);
        }
        catch (Throwable e)
        {
            String message = "Unexpected error during validation: " + e.getClass().getName();
            if (e.getMessage() != null)
            {
                message += ": " + e.getMessage();
            }

            instance.addInstanceError(message);
        }

        if (!instance.isValid())
        {
            throw new ValidationException(instance, "");
        }

        return instance;
    }

    public CompositeType getCompositeType(Class<? extends Configuration> clazz)
    {
        CompositeType type = typeRegistry.getType(clazz);
        if (type == null)
        {
            throw new IllegalArgumentException("Class '" + clazz + "' is not registered as a type");
        }
        return type;
    }

    public void setConfigurationTemplateManager(ConfigurationTemplateManager configurationTemplateManager)
    {
        this.configurationTemplateManager = configurationTemplateManager;
    }

    public void setConfigurationReferenceManager(ConfigurationReferenceManager configurationReferenceManager)
    {
        this.configurationReferenceManager = configurationReferenceManager;
    }

    public void setConfigModelBuilder(ConfigModelBuilder configModelBuilder)
    {
        this.configModelBuilder = configModelBuilder;
    }

    public void setTypeRegistry(TypeRegistry typeRegistry)
    {
        this.typeRegistry = typeRegistry;
    }
}
