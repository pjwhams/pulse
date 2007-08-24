package com.zutubi.pulse.webwork.mapping;

import java.util.HashMap;
import java.util.Map;

/**
 */
public class StageLogsActionResolver extends ParameterisedActionResolver
{
    private String stage;

    public StageLogsActionResolver(String stage)
    {
        super("tailRecipeLog");
        this.stage = stage;
    }

    public Map<String, String> getParameters()
    {
        Map<String, String> params = new HashMap<String, String>(1);
        params.put("stageName", stage);
        return params;
    }
}
