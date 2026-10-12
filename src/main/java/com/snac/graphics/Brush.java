package com.snac.graphics;

import java.awt.*;

/**
 * The brush is the only tool you really need to draw on a window created by a {@link Renderer}<br>
 * A Brush instance is passed to an {@link Renderable} by the used {@link Renderer} (or {@link Canvas})
 * <p>I recommend also looking at the named classes and their documentation.</p>
 * <p>
 * <b>Why do rendered objects that are moving look so choppy even though they are rendered at 60fps+?</b>
 * Try using interpolation. (See {@link Renderer#getInterpolatedX(float, float, float)} and {@link Renderer#getInterpolatedY(float, float, float)})
 *
 * @param <I> Generic image
 */
public interface Brush<I> {

    /**
     * Sets the color of the Brush
     *
     * @param color The color you want to set
     */
    void setColor(Color color);

    /**
     * Draws an rectangle
     *
     * @param x      Rectangle X-Pos on the window
     * @param y      Rectangle Y-Pos on the window
     * @param width  Rectangle width
     * @param height Rectangle height
     * @param filled Set to {@code true} the rectangle will be filled, otherwise only the outline will get drawn
     */
    void drawRectangle(int x, int y, int width, int height, boolean filled);

    /**
     * Draws an Image. Also see {@link ImageLoader}.
     *
     * @param image  The image you want to draw
     * @param x      Image X-Pos on the window
     * @param y      Image Y-Pos on the window
     * @param width  Image width
     * @param height Image height
     */
    void drawImage(I image, int x, int y, int width, int height);

    /**
     * Draws an Arc
     *
     * @param x          Arc X-Pos on the window
     * @param y          Arc Y-Pos on the window
     * @param width      Arc width
     * @param height     Arc height
     * @param startAngle Arc start angle
     * @param arcAngle   Arc angle
     * @param filled     Set to {@code true} the arc will be filled, otherwise only the outline will get drawn
     */
    void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle, boolean filled);

    /**
     * Draws a line from P(x1 | y1) to P(x2 | y2)
     *
     * @param x1 Startpoint X-Pos
     * @param y1 Startpoint Y-Pos
     * @param x2 Endpoint X-Pos
     * @param y2 Endpoint Y-Pos
     */
    void drawLine(int x1, int y1, int x2, int y2);

    /**
     * Draws a polygon
     *
     * @param points Every keypoint
     * @param filled Set to {@code true} the polygon will be filled, otherwise only the outline will get drawn
     */
    void drawPolygon(Point[] points, boolean filled);

    /**
     * Draws an oval
     *
     * @param x      Oval X-Pos on the window
     * @param y      Oval Y-Pos on the window
     * @param width  Oval width
     * @param height Oval height
     * @param filled Set to {@code true} the oval will be filled, otherwise only the outline will get drawn
     */
    void drawOval(int x, int y, int width, int height, boolean filled);

    /**
     * Same as {@link #drawRectangle(int, int, int, int, boolean)} but round
     */
    void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight, boolean filled);

    /**
     * Draws one Pixel
     *
     * @param x Pixel X-Pos on the window
     * @param y Pixel Y-Pos on the window
     */
    void drawPixel(int x, int y);

    /**
     * Draws one Pixel.<br>
     * Prevent creating many Point instances in the rendering loop! As this would lead to performance issues.
     *
     * @param location The location of the pixel
     */
    void drawPixel(Point location);

    /**
     * Draws one or more Pixels.<br>
     * Prevent creating many Point-array instances in the rendering loop! As this would lead to performance issues.
     *
     * @param locations Array of every Pixel location
     */
    void drawPixels(Point[] locations);

    /**
     * Draws one or more Pixels.<br>
     * Prevent creating many Point-array instances in the rendering loop! As this would lead to performance issues.
     *
     * @param locations Array of every Pixel location
     * @param colors    The colors of the pixels.
     *                  For example,
     *                  the value at index 0 in the location-array will be drawn with the color from index 0 in the colors-array.
     *                  If the colors-array doesn't have enough colors for every location,
     *                  the color set by {@link #setColor(Color)} will be used.
     */
    void drawPixels(Point[] locations, int[] colors);

    /**
     * @return The current line size. The default line size may be different depending on the used brush
     */
    float getSize();

    /**
     * Sets a new line size
     *
     * @param size The new line size you want to set
     */
    void setSize(float size);

    /**
     * Resets every globalen brush setting that was made by {@link #setColor(Color)} for example.
     */
    void reset();

    /**
     * @return the used {@link Renderer}
     */
    Renderer<I> getRenderer();
}