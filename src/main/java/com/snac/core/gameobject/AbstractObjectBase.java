package com.snac.core.gameobject;

import com.snac.graphics.Brush;
import com.snac.graphics.Renderable;
import com.snac.graphics.Renderer;
import com.snac.util.Attachable;
import com.snac.util.HitBox;
import com.snac.util.Vector2D;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.Serializable;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Base class for renderable and updatable game objects.
 * <p>
 * Subclasses are expected to provide concrete behavior by implementing the lifecycle methods.
 *
 * <p><strong>Note</strong></p>
 * <ul>
 *   <li>{@link #internalCreate(GameObjectManager)} is invoked by the framework to register the object and then call {@link #onCreate()}.</li>
 *   <li>{@link #internalUpdate(double)} is invoked by the framework every tick and delegates to {@link #onUpdate(double)}.</li>
 * </ul>
 * <p>
 * To provide functionality for your game objects, you need to add them to a valid {@link GameObjectManager} instance.
 *
 * <p>
 * <b>Note:</b> By removing unused objects via {@link GameObjectManager#destroyGameObject(AbstractObjectBase)} you can save resources.
 * To implement such features you can use a cache (like {@link com.snac.data.runtime.caching.Cache Cache} with {@link com.snac.data.runtime.caching.CacheListener CacheListener} for custom functionality)
 * </p>
 *
 * @param <I> Type of the visual asset associated with this object (e.g., image or sprite handle).
 */
@Slf4j
@Getter
public abstract class AbstractObjectBase<I> extends Attachable<AbstractObjectBase<I>> implements Renderable<I>, Serializable {

    /**
     * Maximum distance from the center of the window (created by the
     * {@link com.snac.graphics.Renderer Renderer}) at which this object still gets updated.
     * <p>
     * If the distance is greater than this value, {@link #disabled} is set to {@code true}
     * and {@link #onUpdate(double)} won't be called anymore.
     * Moving the object back within range re-enables updates.
     * </p>
     * <p>
     * Default value: {@code (screen size / 2) + object width + 200}.
     * See also {@link #internalCreate(GameObjectManager)} and {@link #onPositionChange(double, double)}.
     * </p>
     * <p>
     * Setting this to {@code 0} or lower disables the check, meaning the object
     * always gets updated regardless of distance.
     * </p>
     */
    protected int tickDistance;

    /**
     * Similar to {@link #tickDistance}, but only affects rendering.
     * <p>
     * If the distance is exceeded, {@link #visible} is set to {@code false}.
     * {@link #disabled} is not affected.
     * </p>
     * <p>
     * Setting this to {@code 0} or lower disables the check, meaning the object
     * is always rendered regardless of distance.
     * </p>
     */
    protected int renderDistance;

    /**
     * Whether this object is updated or not.
     * If {@code true}, {@link #onUpdate(double)} will no longer be called.
     * <p>
     * See also {@link #tickDistance}, {@link #internalUpdate(double)} and
     * {@link #onPositionChange(double, double)}.
     * </p>
     */
    protected volatile boolean disabled;

    /**
     * Whether this object is rendered or not.
     * If {@code false}, it won't be drawn.
     * <p>
     * See also {@link Renderable#visible()}, {@link #renderDistance} and
     * {@link #onPositionChange(double, double)}.
     * </p>
     */
    @Setter
    @Getter(AccessLevel.NONE)
    protected volatile boolean visible;

    /**
     * Position of the object relative to the window the object is rendered on
     * (Also see the {@link com.snac.graphics.Renderer Renderer} you are using
     * ({@link com.snac.graphics.impl.SwingRenderer SwingRenderer} by default if you're using the default
     * {@link com.snac.core.Glace Glace} configurations)).<br>
     * Never {@code null}; defaults to (0,0) if not provided.
     */
    protected final Vector2D position;

    /**
     * Object width in pixels.
     */
    protected volatile int width;

    /**
     * Object height in pixels.
     */
    protected volatile int height;

    /**
     * Hitbox used for collision or spatial queries,
     * initialized from the rounded position and current size.
     */
    protected final HitBox hitBox;

    /**
     * Whether the hitbox is rendered or not.
     * Also see {@link #showHitBox()} and {@link #hideHitBox()}
     */
    protected boolean showHitBox;

    /**
     * Wall-clock timestamp (milliseconds since epoch) at which this instance was created.
     */
    private final long timeCreated;

    /**
     * Unique identifier assigned to this instance.
     */
    private final long id;

    /**
     * Optional visual asset bound to this object.
     * Marked {@code volatile} to ensure
     * visibility across threads if updated outside the main loop.
     * May be {@code null}.
     */
    @Setter
    @Nullable
    private volatile I image;

    /**
     * The direction of the object. Also see {@link Direction}
     */
    private float direction;

    /**
     * Manager responsible for the object's lifecycle and orchestration.
     * May be {@code null} this object gets initialized with a {@link GameObjectManager}
     */
    @Nullable
    private GameObjectManager<I> manager;

    /**
     * Creates an object with a default position (0|0), default direction {@link Direction#RIGHT},
     * and minimum dimensions (20x20).
     * <p>
     * Delegates to {@link #AbstractObjectBase(Vector2D, float, int, int)}.
     */
    protected AbstractObjectBase() {
        this(null, Direction.RIGHT.angle, 0, 0);
    }

    /**
     * Creates an object with the provided spatial parameters.
     * The hit box is initialized from the rounded coordinates and the resolved size,
     * and identity fields ({@code timeCreated}, {@code uuid}) are assigned.
     *
     * @param position  initial world position, or {@code null} for (0,0)
     * @param direction initial direction. You can use {@link Direction#direction} if you're confused or something
     * @param width     desired width in pixels; values < 1 resolve to 20
     * @param height    desired height in pixels; values < 1 resolve to 20
     */
    protected AbstractObjectBase(@Nullable Vector2D position, float direction, int width, int height) {
        this.position = new Vector2D(position == null ? new Vector2D(0, 0) : position) {
            @Override
            public void set(double x, double y) {
                onPositionChange(x, y);
                super.set(x, y);
                onPositionChanged(super.getOldX(), super.getOldY());
            }
        };
        this.direction = direction;
        this.disabled = false;
        this.visible = true;
        this.width = width < 1 ? 20 : width;
        this.height = height < 1 ? 20 : height;
        this.hitBox = new HitBox(this.position.getXRound(), this.position.getYRound(), getWidth(), getHeight());
        this.timeCreated = System.currentTimeMillis();
        this.id = GameObjectManager.getNextID();
    }

    /**
     * Callback method invoked before the position of this object changes.<br>
     * Used to implement logic for {@link #renderDistance} and {@link #tickDistance}
     * <p>
     * Subclasses can override this method to perform custom logic or trigger
     *
     * @param newX the new X-value the position is set to
     * @param newY the new Y-value the position is set to
     */
    protected void onPositionChange(double newX, double newY) {
        if (manager == null) {
            log.warn("Object manager is null. How is this possible?");
            return;
        }
        if (manager.getRenderer().getWindowWidth() <= -1) {
            return;
        }
        var renderer = manager.getRenderer();
        var distance = Math.max(Math.abs(this.position.getXRound() - renderer.getWindowWidth() / 2),
                Math.abs(this.position.getYRound() - renderer.getWindowHeight() / 2));

        if (renderDistance > 0) {
            visible = distance < renderDistance;
        }
//        if (tickDistance > 0) {
//            disabled = distance > tickDistance;
//        }
    }

    /**
     * Almost the same as {@link #onPositionChange(double, double)} but this method is invoked <b>after</b> the position changed.<br>
     * Subclasses can override this method to perform custom logic or trigger
     * <p>
     * "Is this method really needed?" No I don't think so, but you never know.
     * </p>
     *
     * @param oldX the X-value before the position got updated
     * @param oldY the Y-value before the position got updated
     */
    protected void onPositionChanged(double oldX, double oldY) {
        updateAttachments(oldX, oldY, getPosition().getX(), getPosition().getY());
    }

    /**
     * Callback method invoked whenever the direction of this object changes.
     * <p>
     * Subclasses can override this method to perform custom logic or trigger.
     * <p>
     * If you're only using static {@link Direction Directions} you can use {@link Direction#getDirection(float)}
     * to translate direction angles to {@link Direction direction enum-values}
     */
    protected void onDirectionChange(float oldDirection, float newDirection) {
    }

    /**
     * Renders this object with the given brush.
     * <p>
     * <b>Recommendation:</b> Use {@link Renderer#getInterpolatedX(float, float, float)} and {@link Renderer#getInterpolatedY(float, float, float)} for smooth rendering</b>
     * </p>
     *
     * @param brush drawing context provided by the renderer
     */
    @Override
    public void render(Brush<I> brush) {
        if (showHitBox) {
            brush.drawRectangle(getHitBox().getX(), getHitBox().getY(), getHitBox().getWidth(), getHitBox().getHeight(), false);
            getHitBox().childAction(child -> {
                brush.drawRectangle(child.getX(), child.getY(), child.getWidth(), child.getHeight(), false);
            });
        }
    }

    /**
     * Update hook invoked by the framework.
     * Implementations should advance the object's state according to the elapsed time
     * and game logic.
     * Avoid blocking operations.
     *
     * @param deltaTime time elapsed since the previous update
     */
    protected abstract void onUpdate(double deltaTime);

    /**
     * One-time initialization hook invoked after the object has been added to a valid {@link GameObjectManager}.
     */
    protected void onCreate() {
        this.tickDistance = (Toolkit.getDefaultToolkit().getScreenSize().width / 2) + getWidth() + 600;
        this.renderDistance = tickDistance;
    }

    /**
     * Cleanup hook invoked when the object is being destroyed.
     */
    protected void onDestroy() {}

    /**
     * Framework-internal update entry point; delegates to {@link #onUpdate(double)}.
     * Not intended to be overridden by subclasses.
     *
     * @param deltaTime time elapsed since the previous update
     */
    void internalUpdate(double deltaTime) {
        if (disabled) return;
        onUpdate(deltaTime);
    }

    /**
     * Framework-internal creation entry point; sets the manager and calls {@link #onCreate()}.
     * Not intended to be called directly by subclasses.
     *
     * @param gameObjectManager the manager responsible for this object
     */
    void internalCreate(GameObjectManager<I> gameObjectManager) {
        this.manager = gameObjectManager;
        this.onCreate();
    }

    /**
     * Getter for the hitbox of this object.
     *
     * @return the hitbox updated with the current position and size of this object
     */
    public HitBox getHitBox() {
        hitBox.setBounds(getPosition().getXRound(), getPosition().getYRound(), getWidth(), getHeight());

        return hitBox;
    }

    /**
     * Updates the relative position of attached objects.
     * See {@link Attachable} (and {@link Attachable#childAction(Consumer)})
     *
     * @param oldX old X-value of this object
     * @param oldY old Y-value of this object
     * @param newX new X-value of this object
     * @param newY new Y-value of this object
     */
    public void updateAttachments(double oldX, double oldY, double newX, double newY) {
        childAction(child -> {
            child.position.set(
                    child.position.getX() - oldX + newX,
                    child.position.getY() - oldY + newY);
        });
    }

    /**
     * When called, {@link #showHitBox} is set to {@code true}
     * and the hitbox (and its attached hitboxes) of this object gets rendered in {@link #render(Brush)}.
     */
    public void showHitBox() {
        if (!showHitBox) {
            showHitBox = true;
            log.info("Showing hit box of object {}", getId());
        }
    }

    /**
     * When called, {@link #showHitBox} is set to {@code false}
     * and the hitbox (and its attached hitboxes) of this object gets rendered in {@link #render(Brush)}.
     */
    public void hideHitBox() {
        if (showHitBox) {
            showHitBox = false;
            log.info("Hiding hit box of object {}", getId());
        }
    }

    /**
     * Overrides {@link Renderable#visible()} to implement {@link #renderDistance}
     */
    @Override
    public boolean visible() {
        return visible;
    }

    /**
     * Set a new direction.
     * @param direction the new direction angle
     */
    public void setDirection(float direction) {
        onDirectionChange(this.direction, direction);
        this.direction = direction;
    }

    /**
     * Set a new direction
     * @param direction the new direction
     */
    public void setDirection(Direction direction) {
        onDirectionChange(this.direction, direction.angle);
        this.direction = direction.angle;
    }

    /**
     * Get the current direction as {@link Direction direction enum-value}
     * @return the current direction as {@link Direction direction enum-value}
     * or
     * {@code null} if the current direction angle isn't a valid {@link Direction direction enum-value}
     */
    @Nullable
    public Direction getDirection() {
        return Direction.getDirection(this.direction);
    }

    /**
     * Discrete movement and facing directions.
     * <p>
     * Each enum value holds:
     * </p>
     * <ul>
     *   <li><b>angle</b> — the nominal heading in degrees, where {@code 0} = {@link #RIGHT},
     *   {@code 90} = {@link #UP}, {@code 180} = {@link #LEFT}, and {@code 270} = {@link #DOWN}.
     *   Angles increase counter‑clockwise.</li>
     *   <li><b>deltaX</b> and <b>deltaY</b> — the components of a unit step in this direction,
     *   computed using {@link Math#cos(double)} and {@link Math#sin(double)}.</li>
     * </ul>
     * <p>
     * Note on units: Java's trigonometric functions expect angles in <em>radians</em>.
     * When you call {@link #getDeltaX(float)} or {@link #getDeltaY(float)} directly,
     * provide the angle in radians (e.g., use {@link Math#toRadians(double)} to convert
     * from degrees). The {@link #angle} stored in this enum is expressed in degrees.
     * </p>
     *
     * <b>Note:</b> The entire documentation for this enum was written by JetBrains Junie.
     */
    @Getter
    public enum Direction {
        UP(90),
        DOWN(270),
        LEFT(180),
        RIGHT(0),
        UP_RIGHT(45),
        UP_LEFT(135),
        DOWN_RIGHT(315),
        DOWN_LEFT(255);

        private final float angle;
        private final float deltaX;
        private final float deltaY;

        /**
         * Creates a direction with the given heading.
         *
         * @param angle the heading angle in degrees
         */
        Direction(float angle) {
            this.angle = angle;
            this.deltaX = getDeltaX(angle);
            this.deltaY = getDeltaY(angle);
        }

        /**
         * Computes the X component (cosine) for the given angle.
         *
         * @param angle an angle in <b>radians</b>
         * @return the X component of the unit vector for this angle
         */
        public static float getDeltaX(float angle) {
            return (float) Math.cos(angle);
        }

        /**
         * Computes the Y component (sine) for the given angle.
         *
         * @param angle an angle in <b>radians</b>
         * @return the Y component of the unit vector for this angle
         */
        public static float getDeltaY(float angle) {
            return (float) Math.sin(angle);
        }

        /**
         * Returns the predefined {@code Direction} whose angle exactly equals the given value.
         * <p>
         * The comparison is made using exact floating‑point equality. If you are working with
         * computed values (as opposed to the predefined constants), consider normalizing or using
         * a tolerance if you add your own matching logic.
         * </p>
         *
         * @param angle an angle in degrees
         * @return the matching {@code Direction}, or {@code null} if none matches exactly
         */
        @Nullable
        public static Direction getDirection(float angle) {
            return Arrays.stream(Direction.values())
                    .filter(direction -> direction.angle == angle)
                    .findFirst()
                    .orElse(null);
        }
    }
}