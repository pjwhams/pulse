package com.zutubi.validation.providers;

import com.zutubi.util.AnnotationUtils;
import com.zutubi.util.ClassLoaderUtils;
import com.zutubi.util.bean.DefaultObjectFactory;
import com.zutubi.util.bean.ObjectFactory;
import com.zutubi.validation.FieldValidator;
import com.zutubi.validation.ValidationContext;
import com.zutubi.validation.Validator;
import com.zutubi.validation.ValidatorProvider;
import com.zutubi.validation.annotations.Constraint;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * <class-comment/>
 */
public class AnnotationValidatorProvider implements ValidatorProvider
{
    // unless an object factory is specified, use the default. We may not always be in a
    // happy autowiring context.
    private ObjectFactory objectFactory = new DefaultObjectFactory();
    private Map<Class, List<Validator>> cache = Collections.synchronizedMap(new HashMap<Class, List<Validator>>());

    public void setObjectFactory(ObjectFactory objectFactory)
    {
        this.objectFactory = objectFactory;
    }

    public List<Validator> getValidators(Object obj, ValidationContext context)
    {
        return traverse(obj.getClass(), new HashSet<Class>());
    }

    public List<Validator> traverse(Class clazz, Set<Class> checked)
    {
        List<Validator> validators = new LinkedList<Validator>();

        if (checked.contains(clazz) || clazz.equals(Object.class))
        {
            return validators;
        }

        // traverse our way up the hierarchy.
        if (clazz.isInterface())
        {
            for (Class interfaceClass : clazz.getInterfaces())
            {
                validators.addAll(traverse(interfaceClass, checked));
            }
        }
        else
        {
            validators.addAll(traverse(clazz.getSuperclass(), checked));
        }

        // traverse across the hierarchy
        for (Class interfaceClass : clazz.getInterfaces())
        {
            if (!checked.contains(interfaceClass))
            {
                validators.addAll(traverse(interfaceClass, checked));
            }
        }

        // analyse the class
        if (!checked.contains(clazz))
        {
            validators.addAll(analyze(clazz));
            checked.add(clazz);
        }

        return validators;
    }

    public List<Validator> analyze(Class clazz)
    {
        // validators are based on the runtime type of the object, not the runtime state, so we can happily cache
        // the details.
        List<Validator> validators = cache.get(clazz);
        if(validators == null)
        {
            validators = new LinkedList<Validator>();
            try
            {
                Class stopClass = clazz.getSuperclass();
                for (PropertyDescriptor descriptor : Introspector.getBeanInfo(clazz, stopClass).getPropertyDescriptors())
                {
                    // the property must be readable for us to be able to validate it.
                    Method read = descriptor.getReadMethod();
                    if (read != null)
                    {
                        List<Annotation> constraints = new LinkedList<Annotation>();
                        constraints.addAll(constraintsOn(read));
                        Method write = descriptor.getWriteMethod();
                        if (write != null)
                        {
                            constraints.addAll(constraintsOn(write));
                        }

                        // analyse the field (if it exists).
                        try
                        {
                            Field field = clazz.getDeclaredField(descriptor.getName());
                            constraints.addAll(constraintsOn(field));
                        }
                        catch (NoSuchFieldException e)
                        {
                            // noop
                        }

                        // convert constraints into validators.
                        validators.addAll(validatorsFromConstraints(clazz, constraints, descriptor));
                    }
                }

                cache.put(clazz, validators);
            }
            catch (IntrospectionException e)
            {
                // noop.
            }
        }

        return validators;
    }

    @SuppressWarnings({"unchecked"})
    private Collection<Annotation> constraintsOn(AnnotatedElement element)
    {
        if (element == null)
        {
            return Collections.EMPTY_SET;
        }
        
        List<Annotation> constraints = new LinkedList<Annotation>();
        for (Annotation annotation : element.getAnnotations())
        {
            if (isConstraint(annotation))
            {
                constraints.add(annotation);
            }
        }
        return constraints;
    }

    private boolean isConstraint(Annotation annotation)
    {
        return annotation instanceof Constraint || annotation.annotationType().getAnnotation(Constraint.class) != null;
    }

    private List<Validator> validatorsFromConstraints(Class clazz, List<Annotation> constraints, PropertyDescriptor descriptor)
    {
        List<Validator> validators = new LinkedList<Validator>();
        for (Annotation annotation : constraints)
        {
            Constraint constraint;
            if(annotation instanceof Constraint)
            {
                constraint = (Constraint) annotation;
            }
            else
            {
                constraint = annotation.annotationType().getAnnotation(Constraint.class);
            }
            
            for (String validatorClassName : constraint.value())
            {
                try
                {
                    Class<? extends Validator> validatorClass = ClassLoaderUtils.loadAssociatedClass(clazz, validatorClassName);
                    Validator validator = objectFactory.buildBean(validatorClass);
                    AnnotationUtils.setPropertiesFromAnnotation(annotation, validator);
                    if (validator instanceof FieldValidator)
                    {
                        ((FieldValidator)validator).setFieldName(descriptor.getName());
                    }
                    validators.add(validator);
                }
                catch (Exception e)
                {
                    // noop.
                    e.printStackTrace();
                }
            }
        }
        return validators;
    }
}
