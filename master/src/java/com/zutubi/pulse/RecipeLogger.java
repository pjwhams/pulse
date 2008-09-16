package com.zutubi.pulse;

import com.zutubi.pulse.core.model.CommandResult;
import com.zutubi.pulse.core.model.RecipeResult;
import com.zutubi.pulse.core.events.RecipeCompletedEvent;
import com.zutubi.pulse.core.events.RecipeStatusEvent;
import com.zutubi.pulse.core.events.CommandCompletedEvent;
import com.zutubi.pulse.core.events.CommandCommencedEvent;
import com.zutubi.pulse.core.events.RecipeErrorEvent;
import com.zutubi.pulse.core.events.RecipeCommencedEvent;
import com.zutubi.pulse.events.build.*;

/**
 * Creates the combined log of a recipe's execution.
 */
public interface RecipeLogger extends HookLogger
{
    void prepare();

    void log(RecipeAssignedEvent event);
    void log(RecipeCommencedEvent event, RecipeResult result);
    void log(CommandCommencedEvent event, CommandResult result);
    void log(CommandCompletedEvent event, CommandResult result);
    void log(RecipeCompletedEvent event, RecipeResult result);
    void log(RecipeStatusEvent event);
    void log(RecipeErrorEvent event, RecipeResult result);

    void complete(RecipeResult result);

    void collecting(RecipeResult recipeResult, boolean collectWorkingCopy);
    void collectionComplete();

    void cleaning();
    void cleaningComplete();

    void postStage();
    void postStageComplete();

    void done();
}
