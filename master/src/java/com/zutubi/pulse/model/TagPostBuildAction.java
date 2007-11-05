package com.zutubi.pulse.model;

import com.zutubi.pulse.core.FileLoadException;
import com.zutubi.pulse.core.Property;
import com.zutubi.pulse.core.Scope;
import com.zutubi.pulse.core.VariableHelper;
import com.zutubi.pulse.core.config.ResourceProperty;
import com.zutubi.pulse.core.model.ResultState;
import com.zutubi.pulse.core.scm.ScmClient;
import com.zutubi.pulse.core.scm.ScmClientFactory;
import com.zutubi.pulse.core.scm.ScmClientUtils;
import com.zutubi.pulse.prototype.config.project.ProjectConfiguration;

import java.util.List;

/**
 * A post build action that applies a tag to the built revision in the
 * repository.
 */
public class TagPostBuildAction extends PostBuildAction
{
    private String tag;
    private boolean moveExisting;

    private ScmClientFactory scmClientFactory;

    public TagPostBuildAction()
    {
    }

    public TagPostBuildAction(String name, List<ResultState> states, boolean failOnError, String tag, boolean moveExisting)
    {
        super(name, states, failOnError);
        this.tag = tag;
        this.moveExisting = moveExisting;
    }

    protected void internalExecute(ProjectConfiguration projectConfig, BuildResult result, RecipeResultNode recipe, List<ResourceProperty> properties)
    {
        ScmClient client = null;
        try
        {
            String tagName = substituteVariables(tag, result, recipe, properties, projectConfig);
            client = scmClientFactory.createClient(projectConfig.getScm());
            client.tag(result.getRevision(), tagName, moveExisting);
        }
        catch (Exception e)
        {
            addError(e.getMessage());
        }
        finally
        {
            ScmClientUtils.close(client);
        }
    }

    public String getType()
    {
        return "apply tag";
    }

    public PostBuildAction copy()
    {
        TagPostBuildAction copy = new TagPostBuildAction();
        copyCommon(copy);
        copy.tag = tag;
        copy.moveExisting = moveExisting;

        return copy;
    }

    private String substituteVariables(String tag, BuildResult build, RecipeResultNode recipe, List<ResourceProperty> properties, ProjectConfiguration projectConfig) throws FileLoadException
    {
        Scope scope = new Scope();
        scope.add(properties);

        scope.add(new Property("project", projectConfig.getName()));
        scope.add(new Property("number", Long.toString(build.getNumber())));

        // Build or stage
        if(recipe == null)
        {
            scope.add(new Property("status", build.getState().getString()));
        }
        else
        {
            scope.add(new Property("status", recipe.getResult().getState().getString()));
        }

        return VariableHelper.replaceVariables(tag, scope);
    }

    public String getTag()
    {
        return tag;
    }

    public void setTag(String tag)
    {
        this.tag = tag;
    }

    public boolean getMoveExisting()
    {
        return moveExisting;
    }

    public void setMoveExisting(boolean moveExisting)
    {
        this.moveExisting = moveExisting;
    }

    public void setScmClientFactory(ScmClientFactory scmClientFactory)
    {
        this.scmClientFactory = scmClientFactory;
    }
}
