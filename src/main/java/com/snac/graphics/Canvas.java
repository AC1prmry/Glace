package com.snac.graphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Canvas instances can be added to any {@link Renderer}.
 * Canvas can get switched/changed while the {@link Renderer} is rendering.
 * This means you can have different Canvas instances used for different things.
 * For example, one for the game itself,
 * one for the game-over screen and one for the settings menu and switch to the one currently needed.
 * <p>
 * The {@link Renderer} will render every {@link Renderable} added to its current Canvas.
 * </p>
 * Also see {@link Renderer} and {@link Renderable} for more information.
 * <p>
 * <b>Why do rendered objects that are moving look so choppy even though they are rendered at 60fps+?</b>
 * Try using interpolation. (See {@link Renderer#getInterpolatedX(float, float, float)} and {@link Renderer#getInterpolatedY(float, float, float)})
 */
public class Canvas<I> {
    protected final List<Renderable<I>> renderables;
    protected final List<Renderable<I>> renderBuffer;
    protected final ReadWriteLock rwLock;
    protected boolean dirty = false;

    /**
     * Creates a new Canvas instance.
     */
    public Canvas() {
        this.renderables = Collections.synchronizedList(new ArrayList<>());
        this.renderBuffer = new ArrayList<>();
        this.rwLock = new ReentrantReadWriteLock();
    }

    /**
     * Adds a new {@link Renderable} to Canvas.
     *
     * @param renderable The renderable you want to add
     */
    public void addRenderable(final Renderable<I> renderable) {
        renderables.add(renderable);
        sortRenderables();
    }

    /**
     * Removes a {@link Renderable} from Canvas
     *
     * @param renderable The renderable you want to remove
     */
    public void removeRenderable(final Renderable<I> renderable) {
        renderables.remove(renderable);
        sortRenderables();
    }

    /**
     * For thread safety and clarity purposes, this method only returns a copy of the renderables-list.
     *
     * @return A copy of the renderables-list from the canvas
     */
    public List<Renderable<I>> getRenderables() {
        rwLock.readLock().lock();
        try {
            return List.copyOf(renderables);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * @return The renderables used by the Canvas as stream
     */
    public Stream<Renderable<I>> streamRenderables() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(renderables).stream();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Deletes every renderable from Canvas
     */
    public void clearRenderables() {
        renderables.clear();
    }

    /**
     * Sometimes it is useful to know on which exact layer a renderable is rendered.
     * For example, this can help ensure that renderables which depend on each other
     * are rendered with the correct offset relative to one another,
     * or when debugging unexpected or incorrect render behavior.
     * <p>
     *     This method helps by returning the layer(s) the given renderable
     *     is currently rendered on.
     * </p>
     *
     * @param renderable The renderable whose render layer(s) should be determined
     *
     * @return A set of layers the renderable is rendered on.
     * Why {@link List}? Because the same renderable instance can be rendered on multiple layers.
     */
    public List<Integer> getLayer(Renderable<?> renderable) {
        var retu = new ArrayList<Integer>();
        var index = 0;

        rwLock.readLock().lock();
        try {
            var it = renderables.iterator();
            while (it.hasNext()) {
                var r = it.next();
                if (r.equals(renderable)) {
                    retu.add(index);
                }
                index++;
            }
        } finally {
            rwLock.readLock().unlock();
        }
        return retu;
    }

    /**
     * Sorts the renderables
     * related to their {@link com.snac.graphics.Renderable.Priority Priority} and {@link Renderable#layer() layer}.
     * Only called when a drawable is added or removed.
     * <p>Idk what exactly I did here, but it somehow works - at least I think so.</p>
     */
    protected synchronized void sortRenderables() {
        rwLock.writeLock().lock();
        try {
            var updated = new ArrayList<>(renderables);
            updated.sort(Comparator.comparing(Renderable::priority));
            updated.removeIf(r -> r.layer() >= 0);

            renderables.sort(Comparator.comparingInt(Renderable::layer));
            for (var r : renderables) {
                if (r.layer() >= 0) {
                    if (updated.size() > r.layer()) {
                        updated.add(r.layer(), r);
                    } else {
                        updated.add(r);
                    }
                }
            }
            renderables.clear();
            renderables.addAll(updated);
        } finally {
            rwLock.writeLock().unlock();
        }
        dirty = true;
    }

    /**
     * Renders every {@link Renderable}
     *
     * @param brush The brush which is passed on to every renderable
     */
    public void render(Brush<I> brush) {
        if (dirty) {
            rwLock.readLock().lock();
            try {
                renderBuffer.clear();
                renderBuffer.addAll(renderables);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        renderBuffer.stream()
                .filter(Renderable::visible)
                .forEach(r -> {
                    r.render(brush);
                });
    }
}