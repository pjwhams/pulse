package com.zutubi.prototype;

import com.zutubi.prototype.annotation.Format;
import com.zutubi.prototype.type.CollectionType;
import com.zutubi.prototype.type.Type;
import com.zutubi.prototype.type.TypeException;
import com.zutubi.prototype.type.TypeRegistry;
import com.zutubi.prototype.type.CompositeType;
import com.zutubi.prototype.config.ConfigurationPersistenceManager;

/**
 *
 *
 */
public class TableDescriptorFactory
{
    private ConfigurationPersistenceManager configurationPersistenceManager;

    public TableDescriptor createTableDescriptor(CollectionType type) throws TypeException
    {
        TableDescriptor tableDescriptor = new TableDescriptor();
        tableDescriptor.setName(type.getSymbolicName());

        // generate the header row.
        RowDescriptor headerRow = new SingleRowDescriptor();
        headerRow.addDescriptor(new HeaderColumnDescriptor(((CompositeType)type.getCollectionType()).getSymbolicName()));
        headerRow.addDescriptor(new HeaderColumnDescriptor("action", 2));
        tableDescriptor.addDescriptor(headerRow);

        //TODO: check that the user has the necessary Auth to view / execute these actions.

        // generate data row.
        RowDescriptor dataRow = new CollectionRowDescriptor(type);

        // take a look at any annotations defined for the base collection type.
        Formatter defaultFormatter = new SimpleColumnFormatter();
        Type baseType = type.getCollectionType();
        Format format = (Format) baseType.getAnnotation(Format.class);
        if (format != null)
        {
            try
            {
                defaultFormatter = format.value().newInstance();
            }
            catch (Exception e)
            {
                throw new TypeException(e);
            }
        }

        ColumnDescriptor columnDescriptor = new SummaryColumnDescriptor(configurationPersistenceManager);
        columnDescriptor.setFormatter(new AnnotationFormatter(defaultFormatter));

        dataRow.addDescriptor(columnDescriptor);
        dataRow.addDescriptor(new EditColumnDescriptor());
        dataRow.addDescriptor(new DeleteColumnDescriptor());
        tableDescriptor.addDescriptor(dataRow);

        RowDescriptor addRowDescriptor = new SingleRowDescriptor();
        addRowDescriptor.addDescriptor(new AddColumnDescriptor(3));
        tableDescriptor.addDescriptor(addRowDescriptor);

        return tableDescriptor;
    }

    public void setConfigurationPersistenceManager(ConfigurationPersistenceManager configurationPersistenceManager)
    {
        this.configurationPersistenceManager = configurationPersistenceManager;
    }
}
