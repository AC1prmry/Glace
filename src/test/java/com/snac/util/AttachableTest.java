package com.snac.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AttachableTest {

    static class TestAttachable extends Attachable<TestAttachable> {
        public int value = 0;
    }

    private TestAttachable attachable1;
    private TestAttachable attachable2;
    private TestAttachable attachable3;

    @Before
    public void setUp() {
        attachable1 = new TestAttachable();
        attachable2 = new TestAttachable();
        attachable3 = new TestAttachable();
    }

    @Test
    public void testAddAttachment() {
        assertThrows("Objects aren't allowed to be attached to themselves.",
                IllegalArgumentException.class, () -> attachable1.addAttachment(attachable1));

        attachable2.addAttachment(attachable3);
        assertThrows("Objects aren't allowed to be attached when they already are.",
                IllegalArgumentException.class, () -> attachable1.addAttachment(attachable3));

        attachable1.addAttachment(attachable2);
        assertTrue("Attached objects should have a parent.",
                attachable2.isAttached() && attachable2.getParent() == attachable1);
    }

    @Test
    public void testRemoveAttachment() {
        attachable1.addAttachment(attachable2);
        attachable1.removeAttachment(attachable2);
        assertFalse("Detached objects should not have a parent.", attachable2.isAttached());
    }

    @Test
    public void testChildAction() {
        attachable1.addAttachment(attachable2);
        attachable2.addAttachment(attachable3);

        attachable1.childAction(child -> child.value = 1);

        assertNotEquals("Parent value shouldn't have changed", 1, attachable1.value);
        assertEquals("Child value should have been set to 1", 1, attachable2.value);
        assertEquals("Grandchildren value should have recursively been set to 1", 1, attachable3.value);
    }

    @Test
    public void testGetRootParent() {
        attachable1.addAttachment(attachable2);
        attachable2.addAttachment(attachable3);

        assertEquals("Root parent should be attachable1", attachable1, attachable3.getRootParent().get());
    }
}
