package com.snac.core;

import com.snac.core.object.ObjectManager;
import com.snac.graphics.animation.AnimationHandler;
import com.snac.graphics.impl.SwingImageLoader;
import com.snac.graphics.impl.SwingRenderer;
import com.snac.util.Loop;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

//TODO: Make default implementations easier to use (interpolation, ...)
//TODO: Modules
//TODO: Package info
//TODO: General Doc improvements
@Getter
@Setter
@Slf4j
public final class Glace {
    public static final Glace INSTANCE = new Glace();

    private SwingImageLoader imageLoader;
    private SwingRenderer renderer;
    private ObjectManager<BufferedImage> objectManager;
    private AnimationHandler<BufferedImage> animationHandler;

    @Setter(AccessLevel.NONE) private Loop loop;
    @Setter(AccessLevel.NONE) private int currentGameLoopTPS = 0;
    @Setter(AccessLevel.NONE) private boolean started = false;
    private final LocalDateTime startTime;
    private Set<Runnable> shutdownHooks;
    @Setter private Runnable tickHook;

    public void start() {
        start(20);
    }

    public void start(int targetTps) {
        if (started) {
            log.warn("Glace already started. Ignoring start request.");
            return;
        } else {
            started = true;
        }

        shutdownHooks = Collections.synchronizedSet(new HashSet<>());

        loop = new Loop(true, "Glace", () -> shutdownHooks.forEach(Runnable::run), false);

        imageLoader = new SwingImageLoader();
        renderer = new SwingRenderer(60, null, 2);
        objectManager = new ObjectManager<>(renderer);
        animationHandler = new AnimationHandler<>(renderer);

        log.info("Initialized");
        startGameLoop(targetTps);
    }

    public void shutdown() {
        try {
            Loop.getRunningLoops().forEach(Loop::stop);
        } catch (IllegalStateException ignored) {}
        System.exit(0);
    }

    private Glace() {
        startTime = LocalDateTime.now();

        //Register or init annotations and general stuff
    }

    /**
     * May throw {@link NullPointerException NPE} if Glace was instantiated but not {@link #start() started}
     */
    public void tick(double deltaTime) {
        objectManager.tick(deltaTime);
        animationHandler.tick();

        if (tickHook != null) tickHook.run();
    }

    private void startGameLoop(int tps) {
        loop.startTickLoop(tps, (fps, deltaTime) -> {
            tick(deltaTime);
            this.currentGameLoopTPS = fps;
        });
    }

    public long getTimeRunning(ChronoUnit unit) {
        return unit.between(startTime, LocalDateTime.now());
    }
}
