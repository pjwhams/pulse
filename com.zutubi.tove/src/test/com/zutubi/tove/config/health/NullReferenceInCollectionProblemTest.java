package com.zutubi.tove.config.health;

import com.zutubi.tove.type.ReferenceType;
import com.zutubi.tove.type.record.MutableRecord;
import com.zutubi.tove.type.record.MutableRecordImpl;
import com.zutubi.tove.type.record.PathUtils;
import com.zutubi.tove.type.record.Record;

import java.util.Arrays;

public class NullReferenceInCollectionProblemTest extends AbstractHealthProblemTestCase
{
    private static final String PATH = "top";
    private static final String KEY = "key";

    private NullReferenceInCollectionProblem problem;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        problem = new NullReferenceInCollectionProblem(PATH, "message", KEY);
    }

    public void testRecordDoesNotExist()
    {
        problem.solve(recordManager);
    }

    public void testKeyDoesNotExist()
    {
        recordManager.insert(PATH, new MutableRecordImpl());
        problem.solve(recordManager);
    }

    public void testKeyIsNotSimple()
    {
        recordManager.insert(PATH, new MutableRecordImpl());
        String nestedPath = PathUtils.getPath(PATH, KEY);
        recordManager.insert(nestedPath, new MutableRecordImpl());
        problem.solve(recordManager);

        assertTrue(recordManager.containsRecord(nestedPath));
    }

    public void testNoNullReferences()
    {
        final String[] REFERENCES = {"1", "2"};

        MutableRecord record = new MutableRecordImpl();
        record.put(KEY, REFERENCES);
        recordManager.insert(PATH, record);
        problem.solve(recordManager);

        Record after = recordManager.select(PATH);
        assertTrue(Arrays.equals(REFERENCES, (String[]) after.get(KEY)));
    }

    public void testRemovesNullReference()
    {
        MutableRecord record = new MutableRecordImpl();
        record.put(KEY, new String[]{"1", ReferenceType.NULL_REFERENCE});
        recordManager.insert(PATH, record);
        problem.solve(recordManager);

        Record after = recordManager.select(PATH);
        assertTrue(Arrays.equals(new String[]{"1"}, (String[]) after.get(KEY)));
    }

    public void testRemovesMultipleNullReferences()
    {
        MutableRecord record = new MutableRecordImpl();
        record.put(KEY, new String[]{"1", ReferenceType.NULL_REFERENCE, "2", ReferenceType.NULL_REFERENCE});
        recordManager.insert(PATH, record);
        problem.solve(recordManager);

        Record after = recordManager.select(PATH);
        assertTrue(Arrays.equals(new String[]{"1", "2"}, (String[]) after.get(KEY)));
    }
}