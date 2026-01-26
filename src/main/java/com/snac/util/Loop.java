package com.snac.util;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Utility class for creating (game-oriented) loops.
 *
 * <p>This class provides two loop types, each intended for a different part:</p>
 *
 * <ul>
 *   <li>{@link #startTickLoop(int, BiConsumer)} – a loop intended for game
 *       logic, physics, and other simulation updates. The provided target rate
 *       ensures that updates run at a consistent pace, independent of the
 *       rendering speed.
 *       See method documentation for an example</li>
 *
 *   <li>{@link #startFrameLoop(int, BiConsumer)} – a loop intended for
 *       rendering or other visually oriented tasks. A target frame rate may be
 *       used to limit GPU load or synchronize with display refresh rates, but
 *       the loop does not attempt to maintain strict simulation timing.
 *       See method documentation for an example</li>
 * </ul>
 *
 * <h3>Why multiple loops?</h3>
 * <p>
 * Simulation and rendering have different timing requirements. Simulation must
 * process every tick to remain deterministic and avoid inconsistent physics,
 * whereas rendering may freely skip frames because only the latest visual state
 * matters. Separating the loops ensures stable gameplay timing while still
 * allowing rendering to run independently and efficiently.
 * <br><br>
 * Btw due to the two different loops with a different rate your game may seem laggy,
 * although you configured 60 FPS+ (for render loop).
 * You can fix this with <strong>interpolation</strong> - just google it ->
 * This framework provides helper methods for this ({@link com.snac.graphics.Renderer Renderer documentation})
 * </p>
 */
@Slf4j
@Getter
public class Loop {
    private static int loopCount = 1;
    protected final int id;
    protected volatile boolean running;
    protected volatile double alpha;
    @Setter
    protected boolean runOnThread;
    protected final String name;
    protected final Runnable shutdownHook;

    /**
     * Loop constructor. Does constructor stuff.
     *
     * @param runOnThread Set to {@code true}, the loop will run on a separate thread.
     *                    When set to {@code false}, this loop will run on the thread it got started on
     * @param name The name for this loop. Will also be used as thread name (if this loop runs on a thread (set via {@code runOnThread}))
     * @param shutdownHook This runnable gets called when the loop stops
     */
    public Loop(boolean runOnThread, @Nullable String name, @Nullable Runnable shutdownHook) {
        id = loopCount;
        this.running = false;
        this.alpha = 0;
        this.runOnThread = runOnThread;
        this.name = name == null ? "Loop-" + loopCount : name;
        this.shutdownHook = shutdownHook == null ? () -> {} : shutdownHook;

        log.info("Creating new loop. Count: '{}'", loopCount);
        loopCount++;
    }

    /**
     * Starts a loop intended for game logic, physics, and other simulation updates.
     * The provided target rate ensures that updates run at a consistent pace,
     * independent of the rendering speed.
     * <br> For rendering use {@link #startFrameLoop(int, BiConsumer)} instead.
     *
     * <p>
     * Use example:
     * <pre>{@code
     * //Creates new loop instance, which runs on a separate thread named "Test-Loop"
     * var loop = new Loop(true, "Test-Loop", () -> shutdownEverything());
     *
     * loop.startTickLoop(20, //Loop repeats itself 20-times a second
     *         (tps, fixedDelta) -> {
     *             //This block gets called 20-times a second
     *             System.out.println("Current TPS: " + tps);
     *             System.out.println("DeltaTime: " + deltaTime);
     *             //Calculations or something
     *         });
     * }</pre>
     *
     * @param targetTPS Sets the target <b>T</b>icks-<b>P</b>er-<b>S</b>econd (how often this loop (trys to) callback)
     * @param action The action you want to execute every tick
     */
    public void startTickLoop(int targetTPS, BiConsumer<Integer, Double> action) {
        if (running) {
            log.error("This loop instance is already in use. Maybe a frame loop is running? Stop this loop first!");
            return;
        } else {
            running = true;
        }

        execute(() -> {
            final var fixedDelta = 1.0 / targetTPS;
            var accumulator = 0.0D;
            var last = System.nanoTime();
            var secCount = System.currentTimeMillis();
            var ticksThisSecond = 0;
            var lastTPS = targetTPS;

            while (running) {
                var now = System.nanoTime();
                var frameTime = (now - last) * 1e-9;

                last = now;

                if (frameTime > 0.25) frameTime = 0.25;
                accumulator += frameTime;

                while (running && accumulator >= fixedDelta) {
                    ticksThisSecond++;
                    action.accept(lastTPS, fixedDelta);
                    accumulator -= fixedDelta;
                }
                alpha = accumulator / fixedDelta;

                if (System.currentTimeMillis() - secCount >= 1000) {
                    secCount = System.currentTimeMillis();
                    lastTPS = ticksThisSecond;
                    ticksThisSecond = 0;
                }

                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {
                }
            }
        });
    }

    /**
     * Starts a loop intended for rendering or other visually oriented tasks.
     * <p>Take a look at the class docs to see the difference between {@link #startTickLoop(int, BiConsumer)} and this method.</p>
     *
     * <p>
     * Use example:
     * <pre>{@code
     * //Creates new loop instance, which runs on a separate thread named "Test-Loop"
     * var loop = new Loop(true, "Test-Loop", () -> shutdownEverything());
     *
     * loop.startFrameLoop(20, //Loop repeats itself 20-times a second
     *         (fps, deltaTime) -> {
     *             //This block gets called 20-times a second
     *             System.out.println("Current FPS: " + fps);
     *             System.out.println("DeltaTime: " + deltaTime);
     *             //Rendering or something
     *         });
     * }</pre>
     *
     * @param targetFPS Sets the target <b>F</b>rames-<b>P</b>er-<b>S</b>econd (how often this loop (trys to) callback)
     * @param action The action you want to execute every frame
     */
    public Loop startFrameLoop(int targetFPS, BiConsumer<Integer, Double> action) {
        if (running) {
            log.error("This loop instance is already in use. Maybe a tick loop is running? Stop this loop first!");
            return this;
        } else {
            running = true;
        }

        execute(() -> {
            final var frameDuration = 1.0D / targetFPS;
            var last = System.nanoTime();
            var secCount = System.currentTimeMillis();
            var framesThisSecond = 0;
            var lastFPS = targetFPS;

            while (running) {
                var now = System.nanoTime();
                var deltaTime = (now - last) * 1e-9;

                if (deltaTime <= 0) {
                    last = now;
                    continue;
                }
                last = now;

                framesThisSecond++;
                alpha = deltaTime / frameDuration;
                action.accept(lastFPS, deltaTime);

                var target = last + (long) (frameDuration * 1e9);

                while (running && (target - System.nanoTime()) > 2_000_000) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {
                    }
                }

                while (running && (target - System.nanoTime()) > 0) {
                    //busy wait
                }

                if (System.currentTimeMillis() - secCount >= 1000) {
                    secCount = System.currentTimeMillis();
                    lastFPS = framesThisSecond;
                    framesThisSecond = 0;
                }
            }
        });
        return this;
    }

    /**
     * Stops running loops.
     */
    public void stop() {
        if (!running) {
            log.info("Can't stop loop. Loop isn't running!");
            return;
        }
        running = false;
        log.info("Stopping loop");
    }

    /**
     * Internal used method to correctly execute loops.
     */
    protected void execute(Runnable runnable) {
        Runnable finalRun = () -> {
            log.info("Executing loop '{}'", name);
            runnable.run();
            shutdownHook.run();
            log.info("Loop '{}' finished", name);
        };

        if (isRunOnThread()) {
            Executors.newSingleThreadExecutor((r) -> new Thread(r, name))
                    .execute(finalRun);
        } else {
            finalRun.run();
        }
    }
}
