package com.cinnamonbob.web.wizard;

import com.opensymphony.xwork.Validateable;
import com.opensymphony.xwork.ValidationAware;

/**
 * <class-comment/>
 */
public interface WizardState extends Validateable, ValidationAware
{
    void execute();

    void initialise();

    String getStateName();

    String getNextState();

    void clearErrors();
}
