package com.snac.graphics;

import com.snac.util.Loop;

/**
 * This interface provides the structure for a Renderer. <br>
 * A Renderer is meant to only handle one window.
 * If you want to have more windows, you need to have more Renderer(-Instances)
 * <p>Use this interface if you want to create your own Glace-renderer.</p>
 * <p>
 * Short explanation how rendering works here:<br>
 * {@link Renderer} renders the current set {@link Canvas}.
 * This Canvas renders every {@link Renderable} it contains.
 * </p>
 * <p>
 * <b>Why do rendered objects that are moving look so choppy even though they are rendered at 60fps+?</b>
 * Try using interpolation. (See {@link #getInterpolatedX(float, float, float)} and {@link #getInterpolatedY(float, float, float)})
 */
public interface Renderer<I> {

    /**
     * Creates a new window.
     *
     * @param width  The window width
     * @param height The window height
     * @param title  The windows title
     */
    void createWindow(int width, int height, String title);

    /**
     * Move the window to a specific position
     *
     * @param x The new window X-Position
     * @param y The new window Y-Position
     */
    void moveWindow(int x, int y);

    /**
     * Resize the window.
     *
     * @param width  The new window width
     * @param height The new window height
     */
    void resizeWindow(int width, int height);

    /**
     * Destroy the current window
     */
    void destroyWindow();

    /**
     * Set a new {@link Canvas}
     *
     * @param canvas The new canvas
     */
    void setCanvas(Canvas<I> canvas);

    /**
     * @return Current canvas the renderer uses
     */
    Canvas<I> getCanvas();

    /**
     * @return Current maximum FPS the renderer is running on
     */
    int getMaxFPS();

    /**
     * Sets the maximum fps. This value must be higher than 0
     *
     * @param fps The new maximum fps
     */
    void setMaxFPS(int fps);

    /**
     * @return The current FPS
     */
    int getFPS();

    /**
     * @return the delta time between the last render and the current render
     */
    double getDeltaTime();

    /**
     * This method renders the current Canvas
     */
    void render();

    /**
     * @return the current width of the window. {@code <= -1} if no windows exists
     */
    int getWindowWidth();

    /**
     * @return the current width of the window. {@code <= -1} if no windows exists
     */
    int getWindowHeight();

    /**
     * Calculates the interpolated X position between lastX and newX.<br>
     * <a href="https://de.wikipedia.org/wiki/Interpolation_(Mathematik)">Interpolation (Wikipedia)</a>
     * <p>
     * You can also use {@link com.snac.util.Vector2D#getInterpolatedX(float)}
     *
     * @param lastX The previous X position (at the last tick)
     * @param newX The current X position (at the current tick)
     * @param alpha Interpolation factor (0 = lastX, 1 = newX).
     *              If using {@link com.snac.util.Loop}, you can get this value via {@link Loop#getAlpha()}
     *              (if the loop is running, otherwise it will return 0)
     * @return The interpolated X position
     */
    static float getInterpolatedX(float lastX, float newX, float alpha) {
        return lastX + (newX - lastX) * alpha;
    }

    /**
     * Calculates the interpolated Y position between lastY and newY.<br>
     * <a href="https://de.wikipedia.org/wiki/Interpolation_(Mathematik)">Interpolation (Wikipedia)</a>
     * <p>
     * You can also use {@link com.snac.util.Vector2D#getInterpolatedY(float)}
     * @param lastY The previous Y position (at the last tick)
     * @param newY The current Y position (at the current tick)
     * @param alpha Interpolation factor (0 = lastY, 1 = newY).
     *              If using {@link com.snac.util.Loop}, you can get this value via {@link Loop#getAlpha()}
     *              (if the loop is running, otherwise 0)
     * @return The interpolated Y position
     */
    static float getInterpolatedY(float lastY, float newY, float alpha) {
        return getInterpolatedX(lastY, newY, alpha); //Something seems wrong, but don't worry, it works perfectly fine.
    }
}