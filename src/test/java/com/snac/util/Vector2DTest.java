package com.snac.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test class for Vector2D.
 */
public class Vector2DTest {

    @Test
    public void testAddition() {
        Vector2D v1 = new Vector2D(10, 20);
        Vector2D v2 = new Vector2D(5, 5);
        
        v1.add(v2);
        
        assertEquals("X-value after addition is incorrect", 15.0, v1.getX(), 0.001);
        assertEquals("Y-value after addition is incorrect", 25.0, v1.getY(), 0.001);
    }

    @Test
    public void testSubtraction() {
        Vector2D v1 = new Vector2D(10, 20);
        v1.subtract(5, 10);
        
        assertEquals(5.0, v1.getX(), 0.001);
        assertEquals(10.0, v1.getY(), 0.001);
    }

    @Test
    public void testLength() {
        Vector2D v = new Vector2D(3, 4); // Classic 3-4-5 triangle
        assertEquals(5.0, v.length(), 0.001);
    }

    @Test(expected = ArithmeticException.class)
    public void testDivideByZero() {
        Vector2D v = new Vector2D(10, 10);
        v.divide(0); // Should throw an exception
    }

    @Test
    public void testNormalize() {
        Vector2D v = new Vector2D(10, 0);
        v.normalize();
        
        assertEquals(1.0, v.getX(), 0.001);
        assertEquals(0.0, v.getY(), 0.001);
        assertEquals(1.0, v.length(), 0.001);
    }
}
