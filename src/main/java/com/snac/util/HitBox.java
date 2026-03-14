package com.snac.util;

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
public class HitBox extends Attachable<HitBox> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Vector2D pos;
    private double width;
    private double height;

    /**
     * Constructor to create a new HitBox-instance.
     *
     * @param x the initial X-Position of this hitbox
     * @param y the initial Y-Position of this Hitbox
     * @param width the initial Hitbox width
     * @param height the initial Hitbox height
     */
    public HitBox(double x, double y, double width, double height) {
        this(new Vector2D(x, y), width, height);
    }

    //TODO: Docs
    public HitBox(Vector2D position, double width, double height) {
        this.pos = position;
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
        var pos = hitBox.getPos();
        return intersects(pos.getX(), pos.getY(), hitBox.getWidth(), hitBox.getHeight());
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
        var x = getPos().getX();
        var y = getPos().getY();
        return x <= otherX + width &&
                x + getWidth() >= x &&
                y <= otherY + height &&
                y + getHeight() >= y;
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
     * Moves the hitbox position.
     *
     * @param dx the value added to the X-position
     * @param dy the value added to the Y-position
     */
    public void move(double dx, double dy) {
        var oldX = getPos().getX();
        var oldY = getPos().getY();

        onMove(getPos().getX() + dx, getPos().getY() + dy);
        getPos().add(dx, dy);
        onMoved(oldX, oldY);
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
        var oldX = this.x;
        var oldY = this.y;

        onMove(x, y);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        onMoved(oldX, oldY);
    }

    /**
     * Changes the hitbox position to the given X and Y values.
     */
    public void setPosition(double x, double y) {
        var oldX = this.x;
        var oldY = this.y;

        onMove(x, y);
        this.x = x;
        this.y = y;
        onMoved(oldX, oldY);
    }

    /**
     * Sets the hitbox position and size to the position and size of another hitbox.
     *
     * @param hitBox the hitbox from which the position and size gets copied.
     */
    public void setBounds(HitBox hitBox) {
        var oldX = this.x;
        var oldY = this.y;

        onMove(hitBox.getX(), hitBox.getY());
        this.x = hitBox.getX();
        this.y = hitBox.getY();
        this.width = hitBox.getWidth();
        this.height = hitBox.getHeight();
        onMoved(oldX, oldY);
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
            child.move(getX() - oldX, getY() - oldY);
        });
    }

    /**
     * Callback for hitbox movement.
     * Gets called before the hitbox changed its position.
     * Can be overridden to implement custom functionality before the hitbox moves.
     *
     * @param newX the X-value this hitbox will move to
     * @param newY the Y-Value this hitbox will move to
     */
    protected void onMove(double newX, double newY) {
    }

    /**
     * Returns the rounded X coordinate as integer.<br>
     * If the X-Value overflows an int, it gets limited by {@link Vector2D#limitDoubleIntSafe(double)},
     * to prevent an {@link ArithmeticException}
     *
     * @return rounded X (integer)
     */
    public int getXRound() {
        return roundDouble(x);
    }

    /**
     * Returns the rounded Y coordinate as integer.<br>
     * If the Y-Value overflows an int, it gets limited by {@link Vector2D#limitDoubleIntSafe(double)},
     * to prevent an {@link ArithmeticException}
     *
     * @return rounded Y (integer)
     */
    public int getYRound() {
        return roundDouble(y);
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
     * Overridden method from {@link Attachable} ({@link Attachable#childAction(Consumer)}) to prevent an infinite loop,
     * because recursion is already provided in {@link #onMove(double, double)}.
     */
    @Override
    public void childAction(Consumer<HitBox> childAction) {
        if (attachments.isEmpty()) return;
        synchronized (attachments) {
            attachments.forEach(childAction);
        }
    }

    @Override
    public String toString() {
        return "HitBox [x=" + x + ", y=" + y + ", width=" + width + ", height=" + height;
    }
}
