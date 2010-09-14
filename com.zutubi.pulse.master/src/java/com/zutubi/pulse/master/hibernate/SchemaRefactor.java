package com.zutubi.pulse.master.hibernate;

import com.zutubi.pulse.core.util.JDBCUtils;
import com.zutubi.util.logging.Logger;
import org.hibernate.cfg.Environment;
import org.hibernate.connection.ConnectionProvider;
import org.hibernate.connection.ConnectionProviderFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Table;
import org.hibernate.tool.hbm2ddl.DatabaseMetadata;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.tool.hbm2ddl.TableMetadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.io.IOException;

public class SchemaRefactor
{
    private static final Logger LOG = Logger.getLogger(SchemaRefactor.class);

    private MutableConfiguration config = null;
    private Dialect dialect = null;
    private Properties connectionProperties;
    private String defaultCatalog;
    private String defaultSchema;

    private List<Exception> exceptions;

    public SchemaRefactor(MutableConfiguration config, Properties props)
    {
        this.config = config;
        dialect = Dialect.getDialect(props);

        connectionProperties = new Properties();
        connectionProperties.putAll(dialect.getDefaultProperties());
        connectionProperties.putAll(props);

        defaultCatalog = config.getProperty(Environment.DEFAULT_CATALOG);
        defaultSchema = config.getProperty(Environment.DEFAULT_SCHEMA);
    }

    public void sync()
    {
        SchemaUpdate schemaUpdate = new SchemaUpdate(config, connectionProperties);
        schemaUpdate.execute(true, true);
        exceptions = schemaUpdate.getExceptions();
    }

    /**
     * Patch the existing configuration.  These patches are 'additive' only.  Any columns
     * or tables found in the patch that do not already exist will be added.  Anything missing
     * will be ignored.
     *
     * @param mapping the classpath reference to the patch hbm.xml file
     *
     * @throws IOException if there is a problem loading the patch file.
     */
    public void patch(String mapping) throws IOException
    {
        // Ok, so we do this as follows:
        // a) load the patch mapping
        // b) apply it to the existing in memory mapping
        // c) sync the in memory mapping with the database.
        // Note: this falls under the same restrictions as normal hibernate syncs, so
        // any columns that change details will not need to be handled separately.  Only
        // column additions and table additions are included.

        MutableConfiguration config = new MutableConfiguration();
        config.addClassPathMappings(Arrays.asList(mapping));
        config.buildMappings();
        
        Iterator tables = config.getTableMappings();
        while (tables.hasNext())
        {
            Table table = (Table) tables.next();

            // do we have this table?.
            Table existingTable = getTable(table.getName());
            if (existingTable != null)
            {
                Iterator columns = table.getColumnIterator();
                while (columns.hasNext())
                {
                    Column column = (Column) columns.next();

                    Column existingColumn = getColumn(existingTable, column.getName());
                    if (existingColumn == null)
                    {
                        existingTable.addColumn(column);
                    }
                    else
                    {
                        // avoid this case for now - we need to refresh the column with the new definition,
                        // which means updating the in memory column configuration, and then running a refresh.
                    }
                }
            }
            else
            {
                this.config.addTable(table);
            }
        }

        sync();
    }

    public String[] generateSyncSql() throws SQLException
    {
        return (String[]) executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                DatabaseMetadata meta = new DatabaseMetadata(con, dialect);
                return config.generateSchemaUpdateScript(dialect, meta);
            }
        });
    }

    /**
     * currently only supported by the sync method.
     *
     * @return the list of exceptions generated by the sync command.
     */
    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    public void renameTable(final String fromTableName, final String toTableName) throws SQLException
    {
        executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                Table fromTable = getTable(fromTableName);

                // copy schema
                Table toTable = copyTable(con, fromTable, toTableName);

                // reassign fks.
                transferForeignKeys(con, fromTable, toTable);

                // drop original
                dropTable(con, fromTable);
                return null;
            }
        });
    }

    private void transferForeignKeys(Connection connection, Table fromTable, Table toTable) throws SQLException
    {
        DatabaseMetadata meta = new DatabaseMetadata(connection, dialect);

        Iterator i = config.getTableMappings();
        while (i.hasNext())
        {
            Table t = (Table) i.next();
            Iterator fki = t.getForeignKeyIterator();
            while (fki.hasNext())
            {
                ForeignKey fk = (ForeignKey) fki.next();
                Table referencedTable = fk.getReferencedTable();
                if (referencedTable != null && referencedTable == fromTable)
                {
                    TableMetadata tableInfo = meta.getTableMetadata(t.getName(), defaultSchema, defaultCatalog, false);

                    // verify that the fk is actually in the database.
                    if (tableInfo.getForeignKeyMetadata(fk.getName()) == null)
                    {
                        // foreign key does not exist, so do not drop or recreate it.
                        continue;
                    }

                    String sql = fk.sqlDropString(dialect, defaultCatalog, defaultSchema);
                    LOG.info(sql);
                    JDBCUtils.execute(connection, sql);
                    fk.setReferencedTable(toTable);

                    sql = fk.sqlCreateString(dialect, null, defaultCatalog, defaultSchema);
                    LOG.info(sql);
                    JDBCUtils.execute(connection, sql);
                }
            }
        }
    }

    private void conditionalBuildMappings()
    {
        if (!config.getTableMappings().hasNext())
        {
            config.buildMappings();
        }
    }

    /**
     * When a schema change is made to a column, hibernates alter will not update the column. This method handles
     * what hibernate avoids, by synchronising the definition of the column in the database with the definition
     * in the hibernate mapping. Tricky. 
     *
     * NOTE: The mapping definition should represent what you want to column to look like AFTER the refresh.  This is
     * a little different from the other methods which need to know what the mappings are BEFORE the refactor.
     *
     * @param tableName     the name of the table containing the column to be refreshed
     * @param columnName    the name of the column to be refreshed
     * 
     * @throws SQLException is thrown on error
     */
    public void refreshColumn(final String tableName, final String columnName) throws SQLException
    {
        executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                Table table = getTable(tableName);
                Column column = getColumn(table, columnName);
                refreshColumn(con, table, column);
                return null;
            }
        });
    }

    public boolean renameColumn(final String tableName, final String fromColumnName, final String toColumnName) throws SQLException
    {
        return (Boolean)executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                if (JDBCUtils.columnExists(con,  tableName, fromColumnName))
                {
                    Table table = getTable(tableName);
                    Column column = getColumn(table, fromColumnName);
                    renameColumn(con, table, column, toColumnName);
                    return true;
                }
                return false;
            }
        });
    }

    // WARNING: drop column DOES NOT UPDATE the in memory representation of the schema,
    // so any subsequent sync / patch calls will RE-ADD the column.
    public void dropColumn(final String tableName, final String columnName) throws SQLException
    {
        executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                if (JDBCUtils.columnExists(con,  tableName, columnName))
                {
                    Table table = getTable(tableName);
                    Column column = getColumn(table, columnName);
                    dropColumnConstraints(con, table, column);
                    sqlDropColumn(con, table, columnName);
                }
                return null;
            }
        });
    }

    public void dropTable(final String tableName) throws SQLException
    {
        executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                if (JDBCUtils.tableExists(con, tableName))
                {
                    Table table = getTable(tableName);
                    dropTable(con, table);
                }
                return null;
            }
        });
    }

    public void dropSchema() throws SQLException
    {
        executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                String[] sql = config.generateDropSchemaScript(dialect);
                for (String s : sql)
                {
                    LOG.info(s);
                }
                JDBCUtils.executeSchemaScript(con, sql);
                return null;
            }
        });
    }

    public void createSchema() throws SQLException
    {
        executeWithConnection(new Callback()
        {
            public Object execute(Connection con) throws SQLException
            {
                String[] sql = config.generateSchemaCreationScript(dialect);
                for (String s : sql)
                {
                    LOG.info(s);
                }
                JDBCUtils.executeSchemaScript(con, sql);
                return null;
            }
        });
    }

    private Column getColumn(Table table, String columnName)
    {
        Iterator columns = table.getColumnIterator();
        while (columns.hasNext())
        {
            Column column = (Column) columns.next();
            if (column.getName().equals(columnName))
            {
                return column;
            }
        }
        return null;
    }

    private void renameColumn(Connection connection, Table table, Column fromColumn, String toColumnName) throws SQLException
    {
        // a) identify foreign key references.
        List<ForeignKey> droppedConstraints = dropColumnConstraints(connection, table, fromColumn);

        // update table model.
        String fromColumnName = fromColumn.getName();
        fromColumn.setName(toColumnName);

        // add the new column to the table - synchronise the database with the updated schema.
        updateTableSchema(table, connection);

        // copy column data.
        sqlCopyColumn(connection, table.getName(), toColumnName, fromColumnName);

        // recreate the foreign key constraint if it exists.
        recreatedDroppedConstraints(droppedConstraints, connection);

        // d) drop the original column.
        sqlDropColumn(connection, table, fromColumnName);
    }

    private void recreatedDroppedConstraints(List<ForeignKey> droppedConstraints, Connection connection) throws SQLException
    {
        for (ForeignKey columnKey : droppedConstraints)
        {
            String sql = columnKey.sqlCreateString(dialect, config.getMapping(), defaultCatalog, defaultSchema);
            LOG.info(sql);
            JDBCUtils.execute(connection, sql);
        }
    }

    private void sqlCopyColumn(Connection connection, String tableName, String toColumnName, String fromColumnName) throws SQLException
    {
        String sql = "update " + tableName + " set " + toColumnName + " = " + fromColumnName;
        LOG.info(sql);
        JDBCUtils.execute(connection, sql);
    }

    /**
     * Executes a set of alter table statements to synchronise the underlying database table with the hibernate schema.
     * NOTE: This will only add columns that do not already exist.
     * 
     * @param table         the table to be altered
     * @param connection    the database connection providing access to the table
     * 
     * @throws SQLException is thrown on error
     */
    private void updateTableSchema(Table table, Connection connection) throws SQLException
    {
        DatabaseMetadata meta = new DatabaseMetadata(connection, dialect);
        TableMetadata tableInfo = meta.getTableMetadata(table.getName(), defaultSchema, defaultCatalog, false);

        Iterator alterSqls = table.sqlAlterStrings(dialect, config.getMapping(), tableInfo, defaultCatalog, defaultSchema);
        while (alterSqls.hasNext())
        {
            String sql = (String) alterSqls.next();
            LOG.info(sql);
            JDBCUtils.execute(connection, sql);
        }
    }

    private void refreshColumn(Connection connection, Table table, Column column) throws SQLException
    {
        // create a temporary column. Make a temporary change to the in-memory schema to allow this.
        String columnName = column.getName();
        String tmpColumnName = "temporary_" + columnName;
        try
        {
            column.setName(tmpColumnName);
            updateTableSchema(table, connection);
        }
        finally
        {
            column.setName(columnName);            
        }

        // copy data to the temporary column.
        sqlCopyColumn(connection, table.getName(), tmpColumnName, columnName);

        // drop the existing column constraints.
        List<ForeignKey> droppedConstraints = dropColumnConstraints(connection, table, column);

        // drop original column.
        sqlDropColumn(connection, table, columnName);

        // recreate column - with the refreshed schema.
        updateTableSchema(table, connection);
        
        // copy the data back to the refreshed column.
        sqlCopyColumn(connection, table.getName(), columnName, tmpColumnName);

        // re-enable the foreign key constraints.
        recreatedDroppedConstraints(droppedConstraints, connection);

        // drop the temporary column.
        sqlDropColumn(connection, table, tmpColumnName);
    }

    private List<ForeignKey> dropColumnConstraints(Connection connection, Table table, Column column) throws SQLException
    {
        List<ForeignKey> droppedConstraints = new LinkedList<ForeignKey>();
        Iterator fks = table.getForeignKeyIterator();
        while (fks.hasNext())
        {
            ForeignKey fk = (ForeignKey) fks.next();
            if (fk.getColumns().contains(column))
            {
                String sql = fk.sqlDropString(dialect, defaultCatalog, defaultSchema);
                LOG.info(sql);
                JDBCUtils.execute(connection, sql);
                droppedConstraints.add(fk);
            }
        }
        return droppedConstraints;
    }

    private void sqlDropColumn(Connection connection, Table table, String columnName) throws SQLException
    {
        String sql;
        sql = "alter table " + table.getName() + " drop column " + columnName;
        LOG.info(sql);
        JDBCUtils.execute(connection, sql);
    }

    protected Table copyTable(Connection connection, Table fromTable, String toTableName) throws SQLException
    {
        Table toTable = clone(fromTable);
        toTable.setName(toTableName);

        String sql = toTable.sqlCreateString(dialect, config.getMapping(), defaultCatalog, defaultSchema);
        LOG.info(sql);
        JDBCUtils.execute(connection, sql);

        // if there is data to transfer..
        if (JDBCUtils.executeCount(connection, "select * from " + fromTable.getName()) > 0)
        {
            String columnSql = "";
            String sep = "";
            Iterator columns = toTable.getColumnIterator();
            while (columns.hasNext())
            {
                Column column = (Column) columns.next();
                columnSql = columnSql + sep + column.getName();
                sep = ",";
            }

            sql = "insert into " + toTableName + "(" + columnSql + ") select " + columnSql + " from " + fromTable.getName();
            LOG.info(sql);
            JDBCUtils.execute(connection, sql);
        }

        config.addTable(toTable);
        return toTable;
    }

    protected Table clone(Table table)
    {
        Table clone = new Table(table.getName());
        clone.setAbstract(table.isAbstract());
        clone.setCatalog(table.getCatalog());
        clone.setComment(table.getComment());
        clone.setName(table.getName());
        clone.setPrimaryKey(table.getPrimaryKey());
        clone.setQuoted(table.isQuoted());
        clone.setRowId(table.getRowId());
        clone.setSchema(table.getSchema());
        clone.setSubselect(table.getSubselect());

        Iterator columns = table.getColumnIterator();
        while (columns.hasNext())
        {
            Column column = (Column) columns.next();
            clone.addColumn(column);
        }

        Iterator foreignKeys = table.getForeignKeyIterator();
        while (foreignKeys.hasNext())
        {
            ForeignKey key = (ForeignKey) foreignKeys.next();
            clone.createForeignKey(key.getName(), key.getColumns(), key.getReferencedEntityName(), key.getReferencedColumns());
        }

        return clone;
    }

    protected void dropTable(Connection connection, Table table) throws SQLException
    {
        String sql = table.sqlDropString(dialect, defaultCatalog, defaultSchema);
        LOG.info(sql);
        JDBCUtils.execute(connection, sql);
        config.removeTable(table);
    }

    protected Table getTable(String tableName)
    {
        Iterator tableMappings = config.getTableMappings();
        while (tableMappings.hasNext())
        {
            Table table = (Table) tableMappings.next();
            if (table.getName().equals(tableName))
            {
                return table;
            }
        }
        return null;
    }

    protected Object executeWithConnection(Callback c) throws SQLException
    {
        ConnectionProvider connectionProvider = null;
        Connection connection = null;
        try
        {
            connectionProvider = ConnectionProviderFactory.newConnectionProvider(connectionProperties);
            connection = connectionProvider.getConnection();

            conditionalBuildMappings();
            
            return c.execute(connection);
        }
        finally
        {
            JDBCUtils.close(connection);
            if (connectionProvider != null)
            {
                connectionProvider.close();
            }
        }
    }

    protected interface Callback
    {
        Object execute(Connection con) throws SQLException;
    }
}
