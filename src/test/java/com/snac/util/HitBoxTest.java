package com.snac.util;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the HitBox class.
 */
public class HitBoxTest {

    @Test
    public void testIntersection() {
        HitBox box1 = new HitBox(0, 0, 10, 10);
        HitBox box2 = new HitBox(5, 5, 10, 10); // Overlapping
        HitBox box3 = new HitBox(15, 15, 5, 5); // Far away
        HitBox box4 = new HitBox(10, 0, 10, 10); // Touching at the edge

        assertTrue("Box1 should intersect with Box2 (overlapping)", box1.intersects(box2));
        assertFalse("Box1 should not intersect with Box3 (separated)", box1.intersects(box3));
        assertTrue("Box1 should intersect with Box4 (touching edge)", box1.intersects(box4));
    }

    @Test
    public void testIntersectsArea() {
        HitBox box = new HitBox(0, 0, 10, 10);
        
        assertTrue("Should intersect with overlapping area", box.intersects(5, 5, 10, 10));
        assertFalse("Should not intersect with separate area", box.intersects(15, 15, 5, 5));
        assertTrue("Should intersect with area touching edge", box.intersects(10, 0, 10, 10));
    }

    @Test
    public void testSetPos() {
        HitBox box = new HitBox(10, 10, 50, 50);
        box.setPos(20, 30);
        
        assertEquals(20.0, box.getX(), 0.001);
        assertEquals(30.0, box.getY(), 0.001);
    }

    @Test
    public void testResize() {
        HitBox box = new HitBox(0, 0, 10, 10);
        box.resize(20, 30);
        
        assertEquals(20.0, box.getWidth(), 0.001);
        assertEquals(30.0, box.getHeight(), 0.001);
    }

    @Test
    public void testSetBounds() {
        HitBox box1 = new HitBox(0, 0, 10, 10);
        HitBox box2 = new HitBox(50, 60, 100, 200);
        
        box1.setBounds(box2);
        
        assertEquals(50.0, box1.getX(), 0.001);
        assertEquals(60.0, box1.getY(), 0.001);
        assertEquals(100.0, box1.getWidth(), 0.001);
        assertEquals(200.0, box1.getHeight(), 0.001);
    }

    @Test
    public void testAttachmentsMovement() {
        HitBox parent = new HitBox(0, 0, 100, 100);
        HitBox child = new HitBox(10, 10, 20, 20);
        
        parent.addAttachment(child);
        
        // Move parent
        parent.setPos(50, 50);
        
        // Child should move by the same delta (50, 50)
        // Original child pos: 10, 10 -> New child pos: 60, 60
        assertEquals("Child X should have moved with parent", 60.0, child.getX(), 0.001);
        assertEquals("Child Y should have moved with parent", 60.0, child.getY(), 0.001);
    }

    @Test
    public void testRounding() {
        HitBox box = new HitBox(10.7, 20.2, 30.5, 40.9);
        
        assertEquals(11, box.getXRound());
        assertEquals(20, box.getYRound());
        assertEquals(31, box.getWidthRound());
        assertEquals(41, box.getHeightRound());
    }
}
