package com.cinnamonbob.bootstrap.velocity;

import com.cinnamonbob.bootstrap.StartupException;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.FactoryBean;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 
 *
 */
public class VelocityEngineFactoryBean implements FactoryBean
{
    private static final Logger LOG = Logger.getLogger(VelocityEngineFactoryBean.class.getName());

    private static VelocityEngine VELOCITY_ENGINE;

    public Object getObject() throws Exception
    {
        if (VELOCITY_ENGINE == null)
        {
            synchronized(this)
            {
                if (VELOCITY_ENGINE == null)
                {
                    Properties props = new Properties();

                    InputStream input = VelocityEngineFactoryBean.class.getResourceAsStream("velocity.properties");
                    if (input == null)
                    {
                        throw new StartupException("Unable to locate velocity configuration.");
                    }
                    props.load(input);

                    VELOCITY_ENGINE = new VelocityEngine();
                    VELOCITY_ENGINE.init(props);
                }
            }
        }
        return VELOCITY_ENGINE;
    }

    public Class getObjectType()
    {
        return VelocityEngine.class;
    }

    public boolean isSingleton()
    {
        return true;
    }

}
