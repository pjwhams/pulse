package com.cinnamonbob.core2.config;

import com.cinnamonbob.BobException;
import com.cinnamonbob.core2.BuildResult;
import com.cinnamonbob.core2.BuildException;

import java.util.*;

/**
 * 
 *
 */
public class Project
{
    private String defaultRecipe;
    
    private String name;
    
    private String description;
    
    private Map<String, String> properties = new HashMap<String, String>();
    
    private Map<String, Reference> reference = new HashMap<String, Reference>();
            
    private List<Recipe> recipes = new LinkedList<Recipe>();
    
    private List<Schedule> schedules = new LinkedList<Schedule>();
    
    public void setProperty(String name, String value)
    {
        properties.put(name, value);
    }

    public String getProperty(String name)
    {
        return properties.get(name);
    }
    
    public void setReference(String name, Reference ref)
    {
        reference.put(name, ref);
    }
    
    public Reference getReference(String name)
    {
        return reference.get(name);
    }

    public void setDescription(String text)
    {
        description = text;
    }

    public String getDescription()
    {
        return description;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDefaultRecipe()
    {
        return defaultRecipe;
    }

    public void setDefaultRecipe(String defaultRecipe)
    {
        this.defaultRecipe = defaultRecipe;
    }
    
    public void addSchedule(Schedule s)
    {
        schedules.add(s);
    }
    
    public List<Schedule> getSchedules()
    {
        return Collections.unmodifiableList(schedules);
    }
    
    public void addRecipe(Recipe r)
    {
        recipes.add(r);
    }
    
    public List<Recipe> getRecipes()
    {
        return Collections.unmodifiableList(recipes);
    }

    public Recipe getRecipe(String name)
    {
        if (name == null)
        {
            return null;    
        }
            
        for (Recipe recipe: recipes)
        {
            if (name.equals(recipe.getName()))
            {
                return recipe;
            }
        }
        return null;
    }
    
    public BuildResult build() throws BuildException
    {
        return build(defaultRecipe);
    }
    
    public BuildResult build(String recipeName) throws BuildException
    {
        Recipe recipe = getRecipe(recipeName);
        if (recipe == null)
        {
            throw new BuildException("Undefined recipe " + recipeName + " in project " + getName());
        }
        
        try 
        {
            //TODO: support continuing build when errors occur.
            BuildResult buildResult = new BuildResult();
            for (Command command : recipe.getCommands())
            {
                CommandResult result = command.execute();
                buildResult.add(result);
                if (!result.succeeded())
                {
                    buildResult.setSucceeded(false);
                    return buildResult;
                }
            }
            buildResult.setSucceeded(true);
            return buildResult;
        }
        catch (CommandException e)
        {
            throw new BuildException(e);            
        }
    }
    
    public void schedule()
    {
        
    }
    
    /**
     * @param name
     * @return
     */
    public Schedule getSchedule(String name)
    {
        for (Schedule s : schedules)
        {
            if (s.getName().equals(name))
            {
                return s;
            }
        }
        return null;
    }

    /**
     * @param input
     * @return
     * @throws com.cinnamonbob.BobException
     */
    public String replaceVariables(String input) throws BobException
    {
        return VariableHelper.replaceVariables(input, properties);
    }
}
