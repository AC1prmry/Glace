package com.snac.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.function.Consumer;

import static com.snac.util.Vector2D.roundDouble;

/**
 * This class provides basic methods to detect collisions between objects.<br>
 * By extending {@link Attachable}, objects of this class can be attached to each other.
 * <p>
 * Because the shape of a hitbox is always a rectangle you can attach hitboxes to each other to implement more accurate collision detection.
 * However, too many attachments can also negatively impact performance.
 *
 * <p>
 * Basic example how to use this class:
 * <pre>{@code
 *         HitBox hitbox1 = new HitBox(playerX, playerY, width, height);
 *         HitBox hitbox2 = new HitBox(attackerX, attackerY, width, height);
 *
 *         if (hitbox1.intersects(hitbox2)) {
 *             damagePlayer();
 *         }
 * }</pre>
 *
 * By taking a look at {@link com.snac.core.object.AbstractObjectBase} you can also see an example of how to use this class.
 */
@Getter
@AllArgsConstructor
public class HitBox extends Attachable<HitBox> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Vector2D pos;
    private volatile double width;
    private volatile double height;

    /**
     * Constructor to create a new HitBox-instance.
     *
     * @param x the initial X-Position of this hitbox
     * @param y the initial Y-Position of this Hitbox
     * @param width the initial Hitbox width
     * @param height the initial Hitbox height
     */
    public HitBox(double x, double y, double width, double height) {
        this.pos = new Vector2D(x, y) {
            @Override
            public synchronized void set(double x, double y) {
                super.set(x, y);
                onMoved(this.getOldX(), this.getOldY());
            }
        };
        this.width = width;
        this.height = height;
    }

    /**
     * Method to check if this hitbox touches another hitbox.
     *
     * @param hitBox the other hitbox to check for collision
     * @return {@code true} if these two hitboxes touches each other, otherwise {@code false}
     */
    public boolean intersects(HitBox hitBox) {
        return intersects(getX(), getY(), hitBox.getWidth(), hitBox.getHeight());
    }

    /**
     * Method to check if this hitbox touches the given rectangle.
     *
     * @param otherX the X-value of the given rectangle
     * @param otherY the Y-value of the given rectangle
     * @param width the width of the given rectangle
     * @param height the height of the given rectangle
     * @return {@code true} if the hitbox intersects with the given area, otherwise {@code false}
     */
    public boolean intersects(double otherX, double otherY, double width, double height) {
        return getX() <= otherX + width &&
                getX() + getWidth() >= otherX &&
                getY() <= otherY + height &&
                getY() + getHeight() >= otherY;
    }

    /**
     * Changes the hitbox size.
     *
     * @param width the new hitbox width
     * @param height the new hitbox height
     */
    public void resize(double width, double height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Sets the hitbox position and size to the position and size of another hitbox.
     *
     * @param hitBox the hitbox from which the position and size gets copied.
     */
    public void setBounds(HitBox hitBox) {
        setBounds(hitBox.getX(), hitBox.getY(), hitBox.getWidth(), hitBox.getHeight());
    }

    /**
     * Completely overrides the hitbox position and size.
     *
     * @param x the new hitbox X-Value
     * @param y the new hitbox Y-Value
     * @param width the new hitbox width
     * @param height the new hitbox height
     */
    public void setBounds(double x, double y, double width, double height) {
        setPos(x, y);
        this.width = width;
        this.height = height;
    }

    /**
     * Callback for hitbox movement.
     * Gets called after the hitbox changed its position.
     * Can be overridden to implement custom functionality when the hitbox moved.<p>
     * Currently used to update the relative position of attached hitboxes.
     *
     * @param oldX the X-value before the hitbox moved
     * @param oldY the Y-Value before the hitbox moved
     */
    protected void onMoved(double oldX, double oldY) {
        childAction(child -> {
            child.getPos().add(getX() - oldX, getY() - oldY);
        });
    }

    /**
     * Wrapper method for {@link Vector2D#set(double, double)}
     *
     * @param x the new X-Value of this hitbox
     * @param y the new Y-Value of this hitbox
     */
    public void setPos(double x, double y) {
        getPos().set(x, y);
    }

    /**
     * Wrapper method for {@link Vector2D#getX()}
     *
     * @return the X-Value of this hitbox
     */
    public double getX() {
        return getPos().getX();
    }

    /**
     * Wrapper method for {@link Vector2D#getY()}
     *
     * @return the Y-Value of this hitbox
     */
    public double getY() {
        return getPos().getY();
    }

    /**
     * Returns the rounded X coordinate as integer.<br>
     * If the X-Value overflows an int, it gets limited by {@link Vector2D#limitDoubleIntSafe(double)},
     * to prevent an {@link ArithmeticException}
     *
     * @return rounded X (integer)
     */
    public int getXRound() {
        return roundDouble(getX());
    }

    /**
     * Returns the rounded Y coordinate as integer.<br>
     * If the Y-Value overflows an int, it gets limited by {@link Vector2D#limitDoubleIntSafe(double)},
     * to prevent an {@link ArithmeticException}
     *
     * @return rounded Y (integer)
     */
    public int getYRound() {
        return roundDouble(getY());
    }

    /**
     * @return the rounded width-value as integer.
     * If the width-value overflows an int, it gets limited by {@link Vector2D#limitDoubleIntSafe(double)},
     * to prevent an {@link ArithmeticException}
     */
    public int getWidthRound() {
        return roundDouble(width);
    }

    /**
     * @return the rounded height-value as integer.
     * If the height-value overflows an int, it gets limited by {@link Vector2D#limitDoubleIntSafe(double)},
     * to prevent an {@link ArithmeticException}
     */
    public int getHeightRound() {
        return roundDouble(height);
    }

    /**
     * Overrides {@link Attachable#childAction(Consumer)} method to prevent an infinite loop,
     * because recursion is already provided in {@link #onMoved(double, double)}.
     */
    @Override
    public void childAction(Consumer<HitBox> childAction) {
        if (directAttachments.isEmpty()) return;
        synchronized (directAttachments) {
            directAttachments.forEach(childAction);
        }
    }

    @Override
    public String toString() {
        return "HitBox [x=" + getX() + ", y=" + getY() + ", width=" + width + ", height=" + height;
    }
}
