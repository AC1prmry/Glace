package com.snac.util;

import com.snac.graphics.Renderer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

/**
 * Represents a 2-dimensional vector and provides common vector operations.
 * <p>
 * This class supports vector arithmetic such as addition, subtraction,
 * scaling (multiplication and division by a scalar), negation, and normalization.
 * It also provides utility methods for length, distance calculation, and copying.
 * </p>
 *
 * <p>
 * The vector components {@code x} and {@code y} are stored as doubles to allow
 * for precise calculations.
 * </p>
 *
 * <p>
 * Note: This class implements {@link Serializable}, allowing instances to be serialized. <br>
 * Docs written by ChatGPT btw. Sorry, I was just too lazy...
 * </p>
 */
@Getter
@Slf4j
public class Vector2D implements Serializable {
    private volatile double x;
    private volatile double y;
    private volatile double oldX;
    private volatile double oldY;

    /**
     * Constructs a new Vector2D with the specified x and y components.
     *
     * @param x the x component
     * @param y the y component
     */
    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
        this.oldX = x;
        this.oldY = y;
    }

    /**
     * Constructs a new Vector2D by copying the components of another vector.
     *
     * @param other the vector to copy
     */
    public Vector2D(Vector2D other) {
        this.x = other.x;
        this.y = other.y;
        this.oldX = other.oldX;
        this.oldY = other.oldY;
    }

    /**
     * Adds another vector to this vector.
     *
     * @param other the vector to add
     * @return this vector after addition
     */
    public Vector2D add(Vector2D other) {
        set(this.x+other.x, this.y+other.y);
        return this;
    }

    /**
     * Adds x and y values to this vector.
     *
     * @param x the X-Value to add
     * @param y the Y-Value to add
     * @return this vector after addition
     */
    public Vector2D add(double x, double y) {
        set(this.x+x, this.y+y);
        return this;
    }


    /**
     * Subtracts x and y values from this vector.
     *
     * @param x the X-Value to subtract
     * @param y the Y-Value to subtract
     * @return this vector after subtraction
     */
    public Vector2D subtract(double x, double y) {
        set(this.x-x, this.y-y);
        return this;
    }

    /**
     * Subtracts another vector from this vector.
     *
     * @param other the vector to subtract
     * @return this vector after subtraction
     */
    public Vector2D subtract(Vector2D other) {
        set(this.x-other.x, this.y-other.y);
        return this;
    }

    /**
     * Multiplies this vector by a scalar.
     *
     * @param scalar the scalar to multiply by
     * @return this vector after multiplication
     */
    public Vector2D multiply(double scalar) {
        set(this.x*scalar, this.y*scalar);
        return this;
    }

    /**
     * Divides this vector by a scalar.
     * <p>
     * Logs an error if division by zero is attempted but still performs the operation.
     * </p>
     *
     * @param scalar the scalar to divide by
     * @return this vector after division
     */
    public Vector2D divide(double scalar) {
        if (scalar == 0) {
            log.error("Division by zero", new ArithmeticException("Division by zero"));
        }
        set(this.x/scalar, this.y/scalar);
        return this;
    }

    /**
     * Negates this vector (multiplies both components by -1).
     *
     * @return this vector after negation
     */
    public Vector2D negate() {
        set(-this.x, -this.y);
        return this;
    }

    /**
     * Sets the x and y components of this vector and prevents too big values via {@link #limitDoubleIntSafe(double)}.<br>
     * This method is for overriding to implement actions on change.
     * @param x new x value
     * @param y new y value
     */
    public synchronized void set(double x, double y) {
        oldX = this.x;
        oldY = this.y;
        this.x = limitDoubleIntSafe(x);
        this.y = limitDoubleIntSafe(y);
    }

    /**
     * Clamps the given double value to a range that ensures it can be
     * safely rounded and converted to an int without causing an
     * {@link ArithmeticException}.
     *
     * @param value the value to clamp
     * @return a value guaranteed to be safely round-convertible to int
     */
    public static double limitDoubleIntSafe(double value) {
        double minAllowed = (double) Integer.MIN_VALUE - 0.5;
        double maxAllowed = (double) Integer.MAX_VALUE + 0.5;

        return Math.max(minAllowed, Math.min(maxAllowed, value));
    }

    /**
     * Normalizes this vector to have a length of 1 (unit vector).
     * <p>
     * If the vector has zero length, no changes are made.
     * </p>
     *
     * @return this vector after normalization
     */
    public Vector2D normalize() {
        final double length = length();
        if (length == 0) return this;
        return divide(length);
    }

    /**
     * Calculates and returns the Euclidean length (magnitude) of this vector.
     *
     * @return the length of the vector
     */
    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    /**
     * Calculates and returns the squared length of this vector.
     * <p>
     * Useful for performance optimization if exact length is not needed,
     * since it avoids the costly square root operation.
     * </p>
     *
     * @return the squared length of the vector
     */
    public double lengthSquared() {
        return x * x + y * y;
    }

    /**
     * Calculates the Euclidean distance between this vector and another vector.
     *
     * @param other the other vector
     * @return the distance between the two vectors
     */
    public double distanceTo(Vector2D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Checks whether this vector is equal to another object.
     * <p>
     * Two vectors are equal if their x and y components are both exactly equal.
     * </p>
     *
     * @param obj the object to compare with
     * @return {@code true} if equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vector2D other)) return false;

        return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0;
    }

    /**
     * Creates and returns a new copy of this vector.
     *
     * @return a new Vector2D with the same components as this one
     */
    public Vector2D copy() {
        return new Vector2D(this);
    }

    /**
     * Returns a string representation of this vector with three decimal places.
     *
     * @return formatted string like {@code Vector2D(x=1.234, y=5.678)}
     */
    @Override
    public String toString() {
        return String.format("Vector2D(x=%.3f, y=%.3f)", x, y);
    }

    /**
     * Returns the rounded X coordinate of this vector.
     *
     * @return rounded X (integer)
     */
    public int getXRound() {
        return Math.toIntExact(Math.round(x));
    }

    /**
     * Returns the rounded Y coordinate of this vector.
     *
     * @return rounded Y (integer)
     */
    public int getYRound() {
        return Math.toIntExact(Math.round(y));
    }

    /**
     * Returns interpolated X-value. Used for smooth rendering (Should be used as X-value in your render methods).<br>
     *
     * Same as {@link Renderer#getInterpolatedX(float, float, float)} with fewer parameters
     */
    public float getInterpolatedX(float alpha) {
        return Renderer.getInterpolatedX((float) getOldX(), (float) getX(), alpha);
    }

    /**
     * See {@link #getInterpolatedX(float)}
     */
    public float getInterpolatedY(float alpha) {
        return Renderer.getInterpolatedY((float) getOldY(), (float) getY(), alpha);
    }

    /**
     * @return {@code true} if x and y is 0, otherwise {@code false}
     */
    public boolean isZero() {
        return x == 0 && y == 0;
    }
}
