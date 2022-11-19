package io.sniffy.reflection;

import org.junit.Test;

import static io.sniffy.reflection.Unsafe.$;
import static org.junit.Assert.*;

public class UnsafeTest {

    @Test
    public void testGetSunMiscUnsafe() {
        assertNotNull(Unsafe.getSunMiscUnsafe());
    }

    @Test
    public void testModifyPrivateFields() throws UnsafeException {

        Object objectField = new Object();
        int intField = 42;
        boolean booleanField = false;

        ClassWithDifferentFields objectWithDifferentFields = new ClassWithDifferentFields(objectField, intField, booleanField);

        FieldRef<ClassWithDifferentFields, Object> privateObjectFieldRef =
                $(ClassWithDifferentFields.class).field("privateObjectField");

        assertNotNull(privateObjectFieldRef);

        assertEquals(objectField, privateObjectFieldRef.get(objectWithDifferentFields));

        objectField = new Object();
        privateObjectFieldRef.set(objectWithDifferentFields, objectField);
        assertEquals(objectField, privateObjectFieldRef.get(objectWithDifferentFields));
    }

    @Test
    public void testCompareAndSetBooleanField() throws UnsafeException {

        Object objectField = new Object();
        int intField = 42;
        boolean booleanField = false;

        ClassWithDifferentFields objectWithDifferentFields = new ClassWithDifferentFields(objectField, intField, booleanField);

        FieldRef<ClassWithDifferentFields, Boolean> privateBooleanFieldRef =
                $(ClassWithDifferentFields.class).field("privateBooleanField");

        assertNotNull(privateBooleanFieldRef);

        assertFalse(privateBooleanFieldRef.compareAndSet(objectWithDifferentFields, true, true));
        assertFalse(privateBooleanFieldRef.get(objectWithDifferentFields));

        assertTrue(privateBooleanFieldRef.compareAndSet(objectWithDifferentFields, false, true));
        assertTrue(privateBooleanFieldRef.get(objectWithDifferentFields));
    }

    @Test
    public void testModifyPrivateFinalFields() throws UnsafeException {

        Object objectField = new Object();
        int intField = 42;
        boolean booleanField = false;

        ClassWithDifferentFinalFields objectWithDifferentFields = new ClassWithDifferentFinalFields(objectField, intField, booleanField);

        FieldRef<ClassWithDifferentFinalFields, Object> privateObjectFieldRef =
                $(ClassWithDifferentFinalFields.class).field("privateObjectField");

        assertNotNull(privateObjectFieldRef);

        assertEquals(objectField, privateObjectFieldRef.get(objectWithDifferentFields));

        objectField = new Object();
        privateObjectFieldRef.set(objectWithDifferentFields, objectField);
        assertEquals(objectField, privateObjectFieldRef.get(objectWithDifferentFields));
    }

}