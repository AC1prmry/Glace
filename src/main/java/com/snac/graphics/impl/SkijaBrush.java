package com.snac.graphics.impl;

import com.snac.graphics.Brush;
import com.snac.graphics.Renderer;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import lombok.Getter;

import java.awt.*;
import java.awt.Color;

/**
 * Implementation of {@link Brush}. This Brush is for {@link com.snac.graphics.Renderer Renderer} based on Skija.<br>
 * Since an instance of this class is created by the renderer being used
 * and managed together with its {@link com.snac.graphics.Canvas Canvas},
 * you normally don't need to create an instance yourself - unless you're writing your own renderer.
 *
 * @deprecated This class is deprecated and no longer maintained. It was based on Skija/LWJGL,
 *  which caused issues with cross-platform compatibility (maybe skill issue)<br>
 *  Consider using the new {@link SwingBrush Swing-based brush} instead.
 */
@Getter
@Deprecated(forRemoval = true)
public class SkijaBrush implements Brush<Image> {
    private final Canvas skijaCanvas;
    private final long window;
    private final Paint paint;
    private Font font = new Font(Typeface.makeDefault(), 12);
    private final Rect iRect = new Rect(0, 0, 0, 0);
    private float size = 1f;
    private Color color = Color.BLACK;

    /**
     * As said before: Since an instance of this class is created by the renderer being used
     * and managed together with its {@link com.snac.graphics.Canvas},
     * you normally don't need to create an instance yourself - unless you're writing your own renderer.
     * @param skijaCanvas Don't get confused.
     *                    To draw with Skija, you need a {@link Canvas}.
     *                    This {@link Canvas} is mainly for internal use
     *                    and has nothing to do with the {@link com.snac.graphics.Canvas} from Glace
     * @param window The window this brush should draw on
     */
    public SkijaBrush(Canvas skijaCanvas, long window) {
        this.skijaCanvas = skijaCanvas;
        this.window = window;
        this.paint = new Paint();
    }

    public void setFont(Font font) {
        this.font = font;
    }

    /**
     * See {@link Brush#setColor(Color)}
     */
    @Override
    public void setColor(Color color) {
        this.paint.setColor(color.getRGB());
        this.color = color;
    }

    /**
     * See {@link Brush#drawRectangle(int, int, int, int, boolean)}
     */
    @Override
    public void drawRectangle(int x, int y, int width, int height, boolean filled) {
        paint.setStroke(!filled);
        var rect = iRect.withLeft(x).withTop(y).withRight(x + width).withBottom(y + height);
        skijaCanvas.drawRect(rect, paint);
    }

    /**
     * See {@link Brush#drawImage(Object, int, int, int, int)} and {@link SkijaImageLoader}
     */
    @Override
    public void drawImage(Image image, int x, int y, int width, int height) {
        skijaCanvas.drawImageRect(image, Rect.makeXYWH(x, y, width, height));
    }

    /**
     * See {@link Brush#drawArc(int, int, int, int, int, int, boolean)}
     */
    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle, boolean filled) {
        drawArc(x, y, width, height, startAngle, arcAngle, filled, false);
    }

    /**
     * Almost the same as {@link #drawArc(int, int, int, int, int, int, boolean)}, just one more parameter
     * @param includeCenter I don't exactly know what it does, sorry.
     *                      Maybe you can find something in the <a href="https://github.com/HumbleUI/Skija/tree/master/docs">Skija Docs</a>
     *                      Or just test it out
     */
    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle, boolean filled, boolean includeCenter) {
        paint.setStroke(!filled);
        skijaCanvas.drawArc(x, y, x + width, y +height, startAngle, arcAngle, includeCenter, paint);
    }

    /**
     * See {@link Brush#drawLine(int, int, int, int)}
     */
    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        skijaCanvas.drawLine(x1, y1, x2, y2, paint);
    }

    /**
     * See {@link Brush#drawPolygon(Point[], boolean)}
     */
    @Override
    public void drawPolygon(Point[] points,  boolean filled) {
        paint.setStroke(!filled);

        var path = new Path();
        if (points.length > 0) {
            path.moveTo(points[0].x, points[0].y);
            for (int i = 1; i < points.length; i++) {
                path.lineTo(points[i].x, points[i].y);
            }
            path.closePath();
        }

        skijaCanvas.drawPath(path, paint);
    }

    /**
     * See {@link Brush#drawOval(int, int, int, int, boolean)}
     */
    @Override
    public void drawOval(int x, int y, int width, int height, boolean filled) {
        paint.setStroke(!filled);
        skijaCanvas.drawOval(Rect.makeXYWH(x, y, width, height), paint);
    }

    /**
     * See {@link Brush#drawRoundRect(int, int, int, int, int, int, boolean)}
     */
    @Override
    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight, boolean filled) {
        paint.setStroke(!filled);
        skijaCanvas.drawRRect(RRect.makeXYWH(x, y, width, height, arcWidth, arcHeight), paint);
    }

    public void drawText(String text, int x, int y) {
        short[] glyphs = font.getStringGlyphs(text);

        float[] xpos = new float[glyphs.length];
        float currentX = x;
        for (int i = 0; i < glyphs.length; i++) {
            xpos[i] = currentX;
            currentX += font.getWidths(new short[]{glyphs[i]})[0];
        }

        var blob = TextBlob.makeFromPosH(glyphs, xpos, y, font);

        skijaCanvas.drawTextBlob(blob, 0, 0, paint);
    }

    /**
     * See {@link Brush#drawPixel(int, int)}
     */
    @Override
    public void drawPixel(int x, int y) {
        paint.setAntiAlias(false);
        skijaCanvas.drawPoint(x, y, paint);
        paint.setAntiAlias(true);
    }

    /**
     * See {@link Brush#drawPixel(Point)}
     */
    @Override
    public void drawPixel(Point location) {
        drawPixel(location.x, location.y);
    }

    /**
     * See {@link Brush#drawPixels(Point[])} )}
     */
    @Override
    public void drawPixels(Point[] locations) {
        for (var loc : locations) {
            drawPixel(loc);
        }
    }

    /**
     * See {@link Brush#drawPixels(Point[], int[])}
     */
    @Override
    public void drawPixels(Point[] locations, int[] colors) {
        for (int i = 0; i < locations.length; i++) {
            if (colors.length > i) {
                paint.setColor(colors[i]);
                drawPixel(locations[i]);
            } else {
                paint.setColor(getColor().getRGB());
                drawPixel(locations[i]);
            }
        }
    }

    /**
     * See {@link Brush#getSize()}
     */
    @Override
    public float getSize() {
        return size;
    }

    /**
     * See {@link Brush#setSize(float)}
     */
    @Override
    public void setSize(float size) {
        this.size = size;
        paint.setStrokeWidth(size);
    }

    /**
     * See {@link Brush#reset()}
     */
    @Override
    public void reset() {
        paint.reset();
    }

    @Override
    public Renderer<Image> getRenderer() {
        return null;
    }

    /**
     * Enables or disables antialiasing during rendering.
     */
    public void setAntiAliasing(boolean antiAlias) {
        paint.setAntiAlias(antiAlias);
    }
}
