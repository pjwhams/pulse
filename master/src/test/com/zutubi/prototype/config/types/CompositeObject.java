package com.zutubi.prototype.config.types;

import com.zutubi.pulse.prototype.record.SymbolicName;
import com.zutubi.prototype.config.types.SimpleObject;

import java.util.List;
import java.util.Map;

/**
 */
@SymbolicName("Composite")
public class CompositeObject
{
    private String strA;
    private String strB;
    private SimpleObject simple;

    private List<String> list;
    private Map<String, String> map;

    public String getStrA()
    {
        return strA;
    }

    public void setStrA(String strA)
    {
        this.strA = strA;
    }

    public String getStrB()
    {
        return strB;
    }

    public void setStrB(String strB)
    {
        this.strB = strB;
    }

    public SimpleObject getSimple()
    {
        return simple;
    }

    public void setSimple(SimpleObject simple)
    {
        this.simple = simple;
    }

    public List<String> getList()
    {
        return list;
    }

    public void setList(List<String> list)
    {
        this.list = list;
    }

    public Map<String, String> getMap()
    {
        return map;
    }

    public void setMap(Map<String, String> map)
    {
        this.map = map;
    }
}
