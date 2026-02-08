package com.snac.util;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.function.Consumer;

/**
 * This class provides basic methods to detect collisions between objects.<br>
 * By extending {@link Attachable}, objects of this class can be attached to each other.
 * Because the shape of a hitbox is always a rectangle this can be used to attach multiple hitboxes to each other
 * to refine collision detection.
 *
 * <p>
 * Basic example how to use this class:
 * <pre>{@code
 *         var hitbox1 = new HitBox(playerX, playerY, width, height);
 *         var hitbox2 = new HitBox(attackerX, attackerY, width, height);
 *
 *         if (hitbox1.intersects(hitbox2)) {
 *             damagePlayer();
 *         }
 * }</pre>
 * </p>
 *
 * By taking a look at {@link com.snac.core.object.AbstractObjectBase} you can also see an example of how to use this class.
 */
@Getter
public class HitBox extends Attachable<HitBox> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int x;
    private int y;
    private int width;
    private int height;

    /**
     * Constructor to create a new {@link HitBox}-instance.
     *
     * @param x the initial X-Position of this hitbox
     * @param y the initial Y-Position of this Hitbox
     * @param width the initial Hitbox width
     * @param height the initial Hitbox height
     */
    public HitBox(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
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
        return intersects(hitBox.getX(), hitBox.getY(), hitBox.getWidth(), hitBox.getHeight());
    }

    /**
     * Method to check if this hitbox touches the given rectangle.
     *
     * @param x the X-value of the given rectangle
     * @param y the Y-value of the given rectangle
     * @param width the width of the given rectangle
     * @param height the height of the given rectangle
     * @return {@code true} if these two hitboxes touches each other, otherwise {@code false}
     */
    public boolean intersects(int x, int y, int width, int height) {
        return this.x <= x + width &&
                this.x + this.width >= x &&
                this.y <= y + height &&
                this.y + this.height >= y;
    }

    /**
     * Changes the hitbox size.
     *
     * @param width the new hitbox width
     * @param height the new hitbox height
     */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Moves the hitbox position.
     *
     * @param dx the value added to the X-position
     * @param dy the value added to the Y-position
     */
    public void move(int dx, int dy) {
        var oldX = this.x;
        var oldY = this.y;

        onMove(x + dx, y + dy);
        x += dx;
        y += dy;
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
    public void setBounds(int x, int y, int width, int height) {
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
    public void setPosition(int x, int y) {
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
    protected void onMoved(int oldX, int oldY) {
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
    protected void onMove(int newX, int newY) {
    }

    /**
     * Overridden method from {@link Attachable} ({@link Attachable#childAction(Consumer)}) to prevent an infinite loop,
     * because recursion is already provided in {@link #onMove(int, int)}.
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
