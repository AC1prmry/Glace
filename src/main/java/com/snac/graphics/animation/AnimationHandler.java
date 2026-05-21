package com.snac.graphics.animation;

import com.snac.graphics.Canvas;
import com.snac.graphics.Renderer;
import com.snac.util.TryCatch;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Class to manage Animations. Animations created by extending {@link Animation} can be added to this class
 * and will be rendered by the {@link Canvas} of the {@link Renderer} which is passed to the constructor.
 * <p>It is important to pass the <b>same generic image type as used in the {@link Renderer}</b></p>
 *
 * @param <I> Image type
 */
@Slf4j
public class AnimationHandler<I> {
    private final List<Animation<I>> animations;
    @Getter
    @Setter
    private Canvas<I> canvas;
    private final ReentrantReadWriteLock lock;

    /**
     * Creates a new AnimationHandler instance. Normally you only need one instance for your whole project.<br>
     * <b>Please note:</b> Creating an instance is not enough for this handler to work.
     * You need to call the {@link #tick()} method every tick so the animations can update their indices.
     *
     * @param renderer The renderer this handler should use to render the animations.
     *                 As mentioned before, generic types of the renderer must match the types used in this class.
     */
    public AnimationHandler(Renderer<I> renderer) {
        this.animations = Collections.synchronizedList(new ArrayList<>());
        this.canvas = renderer.getCanvas();
        this.lock = new ReentrantReadWriteLock();

        log.info("Initialized");
    }

    /**
     * Plays the given animation.
     *
     * @param animation the animation to play
     */
    public void play(Animation<I> animation) {
        if (!animation.checkValidation()) {
            throw new IllegalStateException("Failed to play animation %s. Animation validation failed!"
                    .formatted(animation.getClass().getSimpleName()));
        }
        animations.add(animation);
        canvas.addRenderable(animation);
        animation.onPlay();
        log.info("Animation {} started", animation.getClass().getSimpleName());
    }

    /**
     * Stops all animations of the given class. Calls {@link Animation#onStop()} on each animation.
     *
     * @param animationClass the class of the animations to stop
     */
    public void stopByClass(Class<? extends Animation<I>> animationClass) {
        List<Animation<I>> snapshot;
        synchronized (animations) {
            snapshot = new ArrayList<>(animations);
            snapshot.stream()
                    .filter(animation -> animation.getClass().equals(animationClass))
                    .forEach(this::stop);
        }
    }

    /**
     * Stops the given animation. Calls {@link Animation#onStop()} on the animation.<br>
     * Remember to stop animations which aren't needed at the moment to save resources.
     *
     * @param animation the animation to stop
     */
    public void stop(Animation<I> animation) {
        if (animations.contains(animation)) {
            animations.remove(animation);
            canvas.removeRenderable(animation);
            animation.onStop();
        }
        log.info("Animation {} stopped", animation.getClass().getSimpleName());
    }

    /**
     * @return A copy of the list of animations managed by this handler.
     */
    public List<Animation<?>> getAnimations() {
        return List.copyOf(animations);
    }

    /**
     * This method must be called every tick to update the animation indices. <b>Otherwise, this handler won't display animations correctly</b><br>
     * It is recommended to tick this method in the default game loop (calculation loop).
     */
    public void tick() {
        lock.readLock().lock();
        TryCatch.tryFinally(
                () -> animations.forEach(Animation::updateIndex),
                () -> lock.readLock().unlock());
    }
}
