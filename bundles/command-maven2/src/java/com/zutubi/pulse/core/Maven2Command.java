package com.zutubi.pulse.core;

import com.zutubi.pulse.core.model.CommandResult;
import com.zutubi.pulse.util.SystemUtils;

import java.io.File;

/**
 * <class-comment/>
 */
public class Maven2Command extends ExecutableCommand
{
    private String goals;
    private Maven2PostProcessor pp = new Maven2PostProcessor("maven2.pp");

    public Maven2Command()
    {
        super(SystemUtils.IS_WINDOWS ? "mvn.bat" : "mvn");
    }

    public void execute(ExecutionContext context, CommandResult cmdResult)
    {
        setExeFromProperty(context, "maven2.bin");

        if (goals != null)
        {
            addArguments(goals.trim().split(" +"));
            cmdResult.getProperties().put("goals", goals);
        }

        ProcessArtifact pa = createProcess();
        pa.setProcessor(new Maven2PostProcessor("maven.pp"));

        super.execute(context, cmdResult);

        try
        {
            context.setVersion(MavenUtils.extractVersion(new File(getWorkingDir(context.getWorkingDir()), "pom.xml"), "version"));
        }
        catch (PulseException e)
        {
            cmdResult.warning(e.getMessage());
        }
    }

    public String getGoals()
    {
        return goals;
    }

    public void setGoals(String goals)
    {
        this.goals = goals;
    }

    public ExpressionElement createSuppressWarning()
    {
        ExpressionElement element = new ExpressionElement();
        pp.addSuppressWarning(element);
        return element;
    }

    public ExpressionElement createSuppressError()
    {
        ExpressionElement element = new ExpressionElement();
        pp.addSuppressError(element);
        return element;
    }

    public Maven2PostProcessor getPp()
    {
        return pp;
    }
}
