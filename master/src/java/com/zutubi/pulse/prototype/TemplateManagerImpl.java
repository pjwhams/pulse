package com.zutubi.pulse.prototype;

import com.zutubi.pulse.prototype.record.Record;
import com.zutubi.pulse.prototype.record.RecordManager;

import java.util.List;

/**
 */
public class TemplateManagerImpl implements TemplateManager
{
    private RecordManager recordManager;

    public TemplateRecord load(final Scope scope, String id, String recordName)
    {
        List<String> idChain = getChain(id);
        TemplateRecord parent = null;

        for(String currentId: idChain)
        {
            TemplateRecord record = (TemplateRecord) recordManager.load(scope, currentId, new TemplateRecordFactory(currentId));
            record.linkToParent(parent);
            parent = record;
        }

        return parent;
    }

    /**
     * @param id the identifier of the child
     * @return as list containing the inheritance chain for the given child,
     *         parents first, finishing with the child itself
     */
    private List<String> getChain(String id)
    {
        throw new RuntimeException("Method not yet implemented.");
    }

    public void store(Record record)
    {
        throw new RuntimeException("Method not yet implemented.");
    }

    public void delete(Record record)
    {
        throw new RuntimeException("Method not yet implemented.");
    }

    public void setRecordManager(RecordManager recordManager)
    {
        this.recordManager = recordManager;
    }
}
