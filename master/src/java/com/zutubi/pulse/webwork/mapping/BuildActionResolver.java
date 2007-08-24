package com.zutubi.pulse.webwork.mapping;

/**
 */
public class BuildActionResolver extends StaticMapActionResolver
{
    public BuildActionResolver(String id)
    {
        super("viewBuild");

        addMapping("summary", new BuildSummaryActionResolver());
        addMapping("details", new BuildDetailsActionResolver());
        addMapping("logs", new BuildLogsActionResolver());
        addMapping("changes", new BuildChangesActionResolver());
        addMapping("tests", new BuildTestsActionResolver());
        addMapping("file", new BuildPulseFileActionResolver());
        addMapping("artifacts", new BuildArtifactsActionResolver());
        addMapping("wc", new BuildWorkingCopyActionResolver());

        addParameter("buildVID", id);
    }
}
