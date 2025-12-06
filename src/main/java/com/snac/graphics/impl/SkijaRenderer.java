package com.snac.graphics.impl;

import com.snac.graphics.Brush;
import com.snac.graphics.Canvas;
import com.snac.graphics.Renderer;
import com.snac.util.Loop;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.impl.Stats;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Implementation of {@link Renderer} based on Skija and LWJGL. See {@link Renderer}-Interface for more information.
 *
 * @deprecated This class is deprecated and no longer maintained. It was based on Skija/LWJGL,
 *  which caused issues with cross-platform compatibility (maybe skill issue)<br>
 *  Consider using the new {@link SwingRenderer Swing-based renderer} instead.
 */
@Getter
@Deprecated(forRemoval = true)
public class SkijaRenderer implements Renderer {
    private long window = 0;
    private volatile Canvas canvas;
    private volatile boolean vsync = false;
    private volatile int maxFps;
    private volatile int fps = 0;
    private int width;
    private int height;
    @Setter
    private float dpi = 1f;
    private Brush<?> brush;
    private DirectContext context;
    private BackendRenderTarget renderTarget;
    private Surface surface;
    private io.github.humbleui.skija.Canvas skijaCanvas;
    @Nullable
    private final ExecutorService executor;

    /**
     * Empty constructor. Creates a new SkijaRenderer instance with default values
     */
    public SkijaRenderer() {
        this(-1, null, Executors.newSingleThreadExecutor());
    }

    /**
     * Constructor... You know how those work - at least I hope so
     * @param maxFPS The maximum FPS the renderer should render on
     * @param canvas Sets the {@link Canvas}. By setting this to {@code null} a new {@link Canvas} will be created
     * @param executor The executor this renderer should run on.
     *                 By setting this to {@code null} this renderer will use the thread the window is created on for the render-loop,
     *                 which is not recommended as this will block the entire thread.
     */
    public SkijaRenderer(int maxFPS, @Nullable Canvas canvas, @Nullable ExecutorService executor) {
        GLFWErrorCallback.createPrint(System.err).set();

        this.canvas = canvas == null ? new Canvas() : canvas;
        this.maxFps = maxFPS <= 0 ? 60 : maxFPS;
        this.executor = executor;

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        //Ez2Log.info(this, "Initialized");
    }

    private void updateDimensions() {
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);

        float[] xScale = new float[1];
        float[] yScale = new float[1];
        glfwGetWindowContentScale(window, xScale, yScale);
        assert xScale[0] == yScale[0] : "Horizontal dpi=" + xScale[0] + ", vertical dpi=" + yScale[0];

        this.width = (int) (width[0] / xScale[0]);
        this.height = (int) (height[0] / yScale[0]);
        this.dpi = xScale[0];
    }

    /**
     * Will automatically start the render-loop.
     * <p>See {@link Renderer#createWindow(int, int, String)} for more information</p>
     */
    @Override
    public void createWindow(int width, int height, @NotNull String title) {
        if (window != 0) {
            //Ez2Log.warn(this, "Could not create window, only one window per renderer is allowed.");
            return;
        }

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        this.window = glfwCreateWindow(width, height, title, 0, 0);
        if (window == 0) {
            throw new IllegalStateException("Unable to create window");
        }

        updateDimensions();
        //Ez2Log.info(this, "Created window. Starting render loop");

        startRenderLoop();
    }

    private void startRenderLoop() {
        var loop = new Loop(true, "Glace-Rendering", null);

        var initRun = new Runnable() {
            @Override
            public void run() {
                glfwMakeContextCurrent(window);
                GL.createCapabilities();
                glfwSwapInterval(vsync ? 1 : 0);
                glfwShowWindow(window);

                context = DirectContext.makeGL();
                glfwSetWindowSizeCallback(window, (window, width, height) -> {
                    updateDimensions();
                    System.out.println("Resize");
                    initSkija();
                    render();
                });
                //Ez2Log.info(this, "Initialized Skija");
                initSkija();
            }
        };

        var renderRun = new BiConsumer<Integer, Double>() {
            @Override
            public void accept(Integer integer, Double dbl) {
                fps = integer;
                render();
                glfwPollEvents();

                if (glfwWindowShouldClose(window)) {
                    loop.stop();
                    //Ez2Log.info(this, "LWJGL has been terminated");
                }
            }
        };

        var shutdownRun = new Runnable() {
            @Override
            public void run() {
                glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
                glfwTerminate();
                GLFWErrorCallback errorCallback = glfwSetErrorCallback(null);
                if (errorCallback != null) errorCallback.free();

                if (context != null) {
                    context.close();
                }

                //Ez2Log.info(this, "Shutting down render loop");
            }
        };

        loop.startFrameLoop(maxFps, renderRun);
    }

    private void initSkija() {
        Stats.enabled = true;

        if (surface != null) {
            surface.close();
        }
        if (renderTarget != null) {
            renderTarget.close();
        }

        renderTarget = BackendRenderTarget.makeGL(
                (int) (width * dpi),
                (int) (height * dpi),
                0,
                8,
                0,
                FramebufferFormat.GR_GL_RGBA8);

        surface = Surface.makeFromBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getDisplayP3(),
                new SurfaceProps(PixelGeometry.RGB_H));

        skijaCanvas = surface.getCanvas();
        brush = new SkijaBrush(skijaCanvas, window);
    }

    /**
     * See {@link Renderer#moveWindow(int, int)}
     */
    @Override
    public void moveWindow(int x, int y) {
        glfwSetWindowPos(window, x, y);
    }

    /**
     * See {@link Renderer#resizeWindow(int, int)}
     */
    @Override
    public void resizeWindow(int width, int height) {
        glfwSetWindowSize(window, width, height);
    }

    /**
     * See {@link Renderer#destroyWindow()}
     */
    @Override
    public void destroyWindow() {
        if (window == 0) {
            //Ez2Log.warn(this, "Could not destroy window, window is not initialized");
            return;
        }
        glfwSetWindowShouldClose(window, true);
        window = 0;
    }

    /**
     * See {@link Renderer#render()}
     */
    @Override
    public synchronized void render() {
        if (getCanvas() == null) return;
        skijaCanvas.clear(0xFFFFFFFF);
        getCanvas().render(brush);
        surface.flushAndSubmit();
        glfwSwapBuffers(window);
    }

    @Override
    public int getWindowWidth() {
        return 0;
    }

    @Override
    public int getWindowHeight() {
        return 0;
    }

    /**
     * Activate VSync. Also see {@link #isVSync()}
     * @param vsync Set to {@code true} VSync is activated, otherwise disabled
     */
    public void setVSync(boolean vsync) {
        this.vsync = vsync;
        glfwSwapInterval(vsync ? 1 : 0);
    }

    /**
     * Also see {@link #setVSync(boolean)}
     * @return {@code true} if VSync is enabled, {@code false} if VSync is disabled
     */
    public boolean isVSync() {
        return vsync;
    }

    /**
     * See {@link Renderer#setCanvas(Canvas)}
     */
    @Override
    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    /**
     * See {@link Renderer#getCanvas()}
     */
    @Override
    public Canvas getCanvas() {
        return canvas;
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
        return 0;
    }
}
