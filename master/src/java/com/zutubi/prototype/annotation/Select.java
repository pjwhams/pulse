package com.zutubi.prototype.annotation;

import com.zutubi.prototype.OptionProvider;
import com.zutubi.pulse.form.FieldType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)

@Field(type = FieldType.SELECT)
@Handler(SelectAnnotationHandler.class)
public @interface Select
{
    Class<? extends OptionProvider> optionProvider();

    public static final int DEFAULT_size = 1;

    public int size() default DEFAULT_size;
}
