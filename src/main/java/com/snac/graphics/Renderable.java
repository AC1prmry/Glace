package com.snac.graphics;

import java.util.Set;

/**
 * This interface is for everything that needs to be drawn to the screen.
 * Renderable-Objects can be added to any {@link Canvas}-instance.
 * <p>
 * For example, any entity can implement this interface and added to an active canvas from any renderer
 * and gets automatically drawn every frame
 * </p>
 * Also see {@link Renderer}
 */
public interface Renderable<I> {

    /**
     * This method is called every frame.
     * Uses the {@link Brush} instance to draw content.<br>
     * The frame rate depends on the one set for the renderer
     * that renders the canvas this {@code Renderable} was added to (by default 60 FPS).
     * <p>
     * It is not recommended to use this method for calculations or any non-rendering logic as this would block the render-loop.<br>
     * The best way is to just use separated loops for rendering and calculations.
     * </p>
     * Since this is the only non-optional method, you can implement this interface directly using a lambda. You're welcome :)
     * <br><br>Not working? Please check:
     * <br>1. This renderable got added to any {@link Canvas}
     * <br>2. The used {@link Canvas} is the current one set by the {@link Renderer} you want to render this on.
     * <br>3. The used {@link Renderer} is running (Window got created, render loop is running)
     *
     * @param brush The brush used for rendering. See the {@link Brush} class for more information.
     */
    void render(Brush<I> brush);

    /**
     * By overriding this method, you can decide if this Renderable is hidden or drawn.<br>
     * The renderable doesn't get removed from the {@link Canvas} by setting this to {@code false}
     *
     * @return {@code true} to draw this renderable, {@code false} to hide it
     */
    default boolean visible() {
        return true;
    }

    /**
     * Sets the {@link Priority Priority} of this renderable.
     * To change the order, specific renderables get drawn (foreground, background, ...).<br>
     * For example, HUD elements can be set to {@code HIGH}, background to {@code LOW}, menus to {@code HIGHEST}
     * <br>Confused? Just don't override this method or set it to {@code DEFAULT}.
     *
     * @return the priority you want to set this Renderable to
     */
    default Priority priority() {
        return Priority.DEFAULT;
    }

    /**
     * To set the specific layer, this renderable should get drawn to.
     * By returning 0 or higher your drawable is set to the specific layer
     * and {@link #priority()} gets ignored.<br>
     * For example, By setting this to {@code 0} this drawable is the first getting rendered. Everything else gets rendered after (above)
     *
     * @return {@code -1} or lower to disable this function and use the {@link Priority Priority},
     * {@code 0 or higher} the renderable is set to the specific layer.
     */
    default int layer() {
        return -1;
    }

    /**
     * Basically just calls {@link Canvas#getLayer(Renderable)}.
     *
     * @param canvas the canvas this renderable is rendered on
     * @return the layer(s) this renderable is rendered on
     */
    default Set<Integer> getLayerAtRuntime(Canvas<?> canvas) {
        return canvas.getLayer(this);
    }

    /**
     * Possible Priorities Renderables can be set to. See {@link #priority()} for more information
     */
    enum Priority {
        LOWEST,
        LOW,
        DEFAULT,
        HIGH,
        HIGHEST;

        /**
         * @param priority The priority you want to know if it's lower.
         * @return {@code true} if the parameter priority is lower, {@code false} if the parameter priority is higher
         */
        public boolean isHigherThen(Priority priority) {
            return this.compareTo(priority) > 0;
        }
    }
}