package com.snac.graphics.impl;

import com.snac.graphics.Canvas;
import com.snac.graphics.Renderer;
import com.snac.util.Loop;
import com.snac.util.TryCatch;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Implementation of {@link Renderer} based on Swing. See {@link Renderer}-Interface for more information.
 * <p>
 * The easiest way to modify the rendering process is by using {@link #setPreRender(Runnable)}, {@link #setRenderLoopAction(BiConsumer)} or {@link #setPostRender(Runnable)}<br>
 * Otherwise, you can extend this class or write your own renderer with the {@link Renderer Renderer interface}
 * </p>
 * <p>
 * <b>Why do rendered objects that are moving look so choppy even though they are rendered at 60fps+?</b>
 * Try using interpolation. (See {@link #getInterpolatedX(float, float, float)} and {@link #getInterpolatedY(float, float, float)})
 */
@Getter
@Slf4j
public class SwingRenderer implements Renderer<BufferedImage> {
    @Nullable
    protected JFrame frame;
    @Nullable
    protected BufferStrategy bufferStrategy;
    @Setter
    protected volatile Canvas<BufferedImage> canvas;
    protected volatile int maxFps;
    protected volatile int fps;
    protected final ExecutorService executor;
    protected final Loop loop;
    protected SwingBrush brush;
    @Setter
    protected Runnable preRender;
    @Setter
    protected Runnable postRender;
    @Setter
    protected BiConsumer<Integer, Double> renderLoopAction;
    protected java.awt.Canvas swingCanvas;
    protected int buffers;

    /**
     * Empty constructor. Creates a new SwingRenderer instance with default values
     */
    public SwingRenderer() {
        this(-1, null, Executors.newSingleThreadExecutor(), 2);
    }

    /**
     * Constructor... You know how those work - at least I hope so
     *
     * @param maxFPS   The maximum FPS the renderer should render on
     * @param canvas   Sets the {@link Canvas}. By setting this to {@code null} a new {@link Canvas} will be created
     * @param executor The executor this renderer should run on.
     *                 By setting this to {@code null} this renderer will use the thread the window is created on for the render-loop,
     *                 which is not recommended as this will block the entire thread.
     */
    public SwingRenderer(int maxFPS, @Nullable Canvas<BufferedImage> canvas, @Nullable ExecutorService executor, int buffers) {
        this.canvas = canvas == null ? new Canvas<>() : canvas;
        this.maxFps = maxFPS <= 0 ? 60 : maxFPS;
        this.executor = executor;
        this.buffers = buffers < 1 ? 2 : buffers;

        this.loop = Loop.builder()
                .runOnThread(executor == null)
                .threadName("Swing-Rendering")
                .build();

        preRender = () -> {
        };
        postRender = () -> log.info("Shutting down render loop");
        renderLoopAction = (fps, deltaTime) -> {
            this.fps = fps;
            render();
        };

        log.info("Initialized");
    }

    /**
     * Will automatically start the render-loop.
     * <p>See {@link Renderer#createWindow(int, int, String)} for more information</p>
     */
    @Override
    public void createWindow(int width, int height, String title) {
        if (frame != null) {
            log.warn("Could not create window, only one window per renderer is allowed.");
            return;
        }

        //System.setProperty("sun.java2d.opengl", "true");

        SwingUtilities.invokeLater(() -> {
            frame = new JFrame();
            swingCanvas = new java.awt.Canvas();
            swingCanvas.setPreferredSize(new Dimension(width, height));
            swingCanvas.setIgnoreRepaint(true);

            frame.setTitle(title);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(swingCanvas);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            frame.validate();
            frame.requestFocus();


            swingCanvas.createBufferStrategy(getBuffers());
            bufferStrategy = swingCanvas.getBufferStrategy();

            brush = new SwingBrush(this, bufferStrategy.getDrawGraphics());

            startRenderLoop();

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    loop.stop();
                    log.info("JFrame has been terminated");
                }
            });
            log.info("Created window and started rendering");
        });
    }

    protected void startRenderLoop() {
        if (!loop.isRunOnThread()) {
            executor.execute(() -> {
                loop.start(preRender, maxFps, renderLoopAction, postRender);
            });
        } else {
            loop.start(preRender, maxFps, renderLoopAction, postRender);
        }
    }

    /**
     * See {@link Renderer#moveWindow(int, int)}
     */
    @Override
    public void moveWindow(int x, int y) {

        runOnEDT(() -> {
            if (frame == null) {
                log.warn("Can't move window, JFrame is null");
                return;
            }

            frame.setLocation(x, y);
        });
    }

    /**
     * See {@link Renderer#resizeWindow(int, int)}
     */
    @Override
    public void resizeWindow(int width, int height) {
        runOnEDT(() -> {
            if (frame == null) {
                log.warn("Can't resize window, JFrame is null");
                return;
            }

            swingCanvas.setPreferredSize(new Dimension(width, height));
            swingCanvas.revalidate();

            frame.pack();

            swingCanvas.createBufferStrategy(getBuffers());
            bufferStrategy = swingCanvas.getBufferStrategy();
        });

    }

    /**
     * See {@link Renderer#destroyWindow()}
     */
    @Override
    public void destroyWindow() {
        if (frame == null) return;

        frame.dispose();
        frame.setVisible(false);
        this.frame = null;

        log.info("Destroyed JFrame");
    }

    /**
     * See {@link Renderer#getMaxFPS()}
     */
    @Override
    public int getMaxFPS() {
        return maxFps;
    }

    /**
     * See {@link Renderer#setMaxFPS(int)}
     */
    @Override
    public void setMaxFPS(int fps) {
        this.maxFps = fps;
    }

    /**
     * See {@link Renderer#getFPS()}
     */
    @Override
    public int getFPS() {
        return fps;
    }

    @Override
    public double getDeltaTime() {
        return loop == null ? 0 : loop.getDeltaTime();
    }

    /**
     * See {@link Renderer#render()}
     */
    @Override
    public void render() {
        if (frame == null
                || frame.getState() == JFrame.ICONIFIED
                || !swingCanvas.isDisplayable()
                || swingCanvas.getWidth() <= 0
                || swingCanvas.getHeight() <= 0
                || getCanvas() == null) {
            return;
        }

        try {
            if (bufferStrategy == null) {
                swingCanvas.createBufferStrategy(buffers);
                bufferStrategy = swingCanvas.getBufferStrategy();
                return;
            }

            boolean done = false;
            while (!done) {
                Graphics2D g = null;
                try {
                    g = (Graphics2D) bufferStrategy.getDrawGraphics();
                    g.clearRect(0, 0, swingCanvas.getWidth(), swingCanvas.getHeight());

                    brush.setGraphics(g);
                    getCanvas().render(brush);

                    Toolkit.getDefaultToolkit().sync();
                    bufferStrategy.show();

                    done = !bufferStrategy.contentsLost();
                } finally {
                    if (g != null) g.dispose();
                }
            }
        } catch (NullPointerException | IllegalStateException e) {
            log.warn("BufferStrategy invalid after resize, will recreate next frame", e);
            bufferStrategy = null;
        }
    }

    /**
     * See {@link Renderer#getWindowWidth()}
     */
    @Override
    public int getWindowWidth() {
        return frame == null ? -1 : frame.getWidth();
    }

    /**
     * See {@link Renderer#getWindowHeight()}
     */
    @Override
    public int getWindowHeight() {
        return frame == null ? -1 : frame.getHeight();
    }

    public static void runOnEDT(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}