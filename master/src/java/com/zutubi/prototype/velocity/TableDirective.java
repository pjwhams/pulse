package com.zutubi.prototype.velocity;

import com.zutubi.prototype.config.ConfigurationPersistenceManager;
import com.zutubi.prototype.config.ConfigurationTemplateManager;
import com.zutubi.prototype.table.FormattingWrapper;
import com.zutubi.prototype.table.TableDescriptor;
import com.zutubi.prototype.table.TableDescriptorFactory;
import com.zutubi.prototype.type.CollectionType;
import com.zutubi.prototype.type.CompositeType;
import com.zutubi.prototype.type.Type;
import com.zutubi.pulse.bootstrap.ComponentContext;
import com.zutubi.util.logging.Logger;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.velocity.context.InternalContextAdapter;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.parser.node.Node;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 *
 */
public class TableDirective extends PrototypeDirective
{
    private ConfigurationPersistenceManager configurationPersistenceManager;
    private ConfigurationTemplateManager configurationTemplateManager;

    private static final Logger LOG = Logger.getLogger(TableDirective.class);

    private Configuration configuration;

    private boolean ajax = false;

    private String path;

    public TableDirective()
    {
        ComponentContext.autowire(this);
    }

    public void setAjax(boolean ajax)
    {
        this.ajax = ajax;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getName()
    {
        return "table";
    }

    public int getType()
    {
        return LINE;
    }

    public boolean render(InternalContextAdapter contextAdapter, Writer writer, Node node) throws IOException, ResourceNotFoundException, ParseErrorException
    {
        try
        {
            Map params = createPropertyMap(contextAdapter, node);
            wireParams(params);

            Type collectionType = configurationPersistenceManager.getType(path);
            
            // lookup the data.
            Collection data = getTableData(path);

            // generate the table descriptor based on the type of the results.
            TableDescriptorFactory tableFactory = new TableDescriptorFactory();
            ComponentContext.autowire(tableFactory);

            TableDescriptor tableDescriptor = tableFactory.create((CompositeType) collectionType.getTargetType());

            Type type = ((CollectionType)collectionType).getCollectionType();
            
            // handle rendering of the freemarker template.
            Map<String, Object> context = initialiseContext(type.getClazz());
            context.put("table", tableDescriptor);
            context.put("path", path);
            context.put("data", data);

            String templateName = "table.ftl";
            if (ajax)
            {
                templateName = "atable.ftl";
            }

            try
            {
                Template template = configuration.getTemplate("prototype/xhtml/" + templateName);
                template.process(context, writer);
            }
            catch (TemplateException e)
            {
                LOG.warning(e);
                throw new ParseErrorException(e.getMessage());
            }

            return true;
        }
        catch (Exception e)
        {
            LOG.warning(e);
            writer.write(renderError("Failed to render form. Unexpected " + e.getClass() + ": " + e.getMessage()));
            return true;
        }
    }

    private Collection getTableData(String path)
    {
        Collection data;
        Object collection = configurationTemplateManager.getInstance(path);
        Type type = configurationPersistenceManager.getType(path).getTargetType();
        
        if (collection instanceof Map)
        {
            Map map = (Map) collection;
            data = map.values();
        }
        else
        {
            data = (Collection) collection;
        }

        // wrap the collection
        List<Object> wrappedData = new LinkedList<Object>();
        if (data != null)
        {
            for (Object instance : data)
            {
                wrappedData.add(new FormattingWrapper(instance, (CompositeType) type));
            }
        }
        return wrappedData;
    }

    public void setFreemarkerConfiguration(Configuration configuration)
    {
        this.configuration = configuration;
    }

    public void setConfigurationPersistenceManager(ConfigurationPersistenceManager configurationPersistenceManager)
    {
        this.configurationPersistenceManager = configurationPersistenceManager;
    }

    public void setConfigurationTemplateManager(ConfigurationTemplateManager configurationTemplateManager)
    {
        this.configurationTemplateManager = configurationTemplateManager;
    }
}
