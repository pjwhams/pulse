package com.zutubi.pulse.master.tove.model;

import com.zutubi.tove.annotations.SymbolicName;
import com.zutubi.tove.config.AbstractConfiguration;
import com.zutubi.tove.type.TypeException;
import com.zutubi.tove.type.TypeRegistry;
import com.zutubi.util.junit.ZutubiTestCase;

import java.util.List;

/**
 *
 *
 */
public class FormDescriptorFactoryTest extends ZutubiTestCase
{
    private TypeRegistry typeRegistry;
    private FormDescriptorFactory descriptorFactory;

    protected void setUp() throws Exception
    {
        super.setUp();

        typeRegistry = new TypeRegistry();
        descriptorFactory = new FormDescriptorFactory();
    }

    protected void tearDown() throws Exception
    {
        typeRegistry = null;
        descriptorFactory = null;

        super.tearDown();
    }

    public void testPasswordField() throws TypeException
    {
        typeRegistry.register(MockPassword.class);
        FormDescriptor formDescriptor = descriptorFactory.createDescriptor("path", "basename", typeRegistry.getType(MockPassword.class), true, "form");

        List<FieldDescriptor> fieldDescriptors = formDescriptor.getFieldDescriptors();
        assertEquals(1, fieldDescriptors.size());
        assertEquals("password", fieldDescriptors.get(0).getType());
    }

    public void testTextField() throws TypeException
    {
        typeRegistry.register(MockText.class);
        FormDescriptor formDescriptor = descriptorFactory.createDescriptor("path", "basename", typeRegistry.getType(MockText.class), true, "form");

        List<FieldDescriptor> fieldDescriptors = formDescriptor.getFieldDescriptors();
        assertEquals(1, fieldDescriptors.size());
        assertEquals("text", fieldDescriptors.get(0).getType());
    }

    @SymbolicName("mockText")
    public static class MockText extends AbstractConfiguration
    {
        private String text;

        public String getText()
        {
            return text;
        }

        public void setText(String text)
        {
            this.text = text;
        }
    }

    @SymbolicName("mockPassword")
    public static class MockPassword extends AbstractConfiguration
    {
        private String password;

        public String getPassword()
        {
            return password;
        }

        public void setPassword(String password)
        {
            this.password = password;
        }
    }

    public static class MockWithReadOnlyField extends AbstractConfiguration
    {
        private String field;

        public String getField()
        {
            return field;
        }

        public void setField(String field)
        {
            this.field = field;
        }
    }
}
