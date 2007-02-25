package com.zutubi.prototype.velocity;

import com.zutubi.prototype.FieldDescriptor;
import com.zutubi.prototype.FormDescriptor;
import com.zutubi.prototype.freemarker.GetTextMethod;
import com.zutubi.prototype.model.Form;
import com.zutubi.prototype.type.Type;
import com.zutubi.prototype.type.TypeException;
import com.zutubi.pulse.i18n.Messages;
import com.zutubi.pulse.util.logging.Logger;
import freemarker.core.DelegateBuiltin;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.velocity.exception.ParseErrorException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class FormDirective extends PrototypeDirective
{
    private static final Logger LOG = Logger.getLogger(FormDirective.class);

    private String action;

    /**
     * The name of this velocity directive.
     *
     * @return name
     */
    public String getName()
    {
        return "pform";
    }

    public int getType()
    {
        return LINE;
    }

    /**
     * The generated forms action attribute.
     * 
     * @param action attribute
     */
    public void setAction(String action)
    {
        this.action = action;
    }

    public String doRender(Type type) throws IOException, ParseErrorException, TypeException
    {
        Object data = configurationPersistenceManager.getInstance(path);

        FormDescriptor formDescriptor = formDescriptorFactory.createDescriptor(type.getSymbolicName());

        // decorate the form to include the symbolic name as a hidden field. This is necessary for
        // configuration. This is probably not the best place for this, but until i think of a better location,
        // here it stays.
        FieldDescriptor hiddenFieldDescriptor = new FieldDescriptor();
        hiddenFieldDescriptor.setName("symbolicName");
        hiddenFieldDescriptor.addParameter("value", type.getSymbolicName());
        hiddenFieldDescriptor.addParameter("type", "hidden");
        formDescriptor.add(hiddenFieldDescriptor);

        Map<String, Object> context = new HashMap<String, Object>();

        Form form = formDescriptor.instantiate(data);
        form.setAction(action);

        context.put("form", form);

        try
        {
            // handle rendering of the freemarker template.
            StringWriter writer = new StringWriter();

            Messages messages = Messages.getInstance(type.getClazz());

            context.put("i18nText", new GetTextMethod(messages));

            // provide some syntactic sweetener by linking the i18n text method to the ?i18n builtin function.
            DelegateBuiltin.conditionalRegistration("i18n", "i18nText");

            Template template = configuration.getTemplate("prototype/xhtml/form.ftl");
            template.process(context, writer);

            return writer.toString();
        }
        catch (TemplateException e)
        {
            LOG.warning(e);
            throw new ParseErrorException(e.getMessage());
        }
    }

}
