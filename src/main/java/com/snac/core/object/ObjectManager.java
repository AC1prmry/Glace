package com.snac.core.object;

import com.snac.graphics.Renderer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Class to manage {@link AbstractObjectBase objects}.
 * For a proper use of this framework you should add your {@link AbstractObjectBase game objects} to a valid instance of this class.
 * This class is thread safe (at least I hope so) and does all the work related to game objects for you.
 *
 * <p>
 * <b>Important Note:</b> It is important to call {@link #tick(double)} every calculation cycle.
 * <br>(Also see {@link com.snac.core.Glace Glace standard implementation})
 * </p>
 *
 * <p>
 * If you want to modify this class, feel free to do so.
 * </p>
 *
 * @param <I> Type of the visual asset associated with this object (e.g., image or sprite handle).
 */
@Slf4j
public class ObjectManager<I> {
    private static long IDs = 0;

    /**
     * This {@link Set} contains all game objects managed by this manager.
     */
    protected final Set<AbstractObjectBase<I>> gameObjects;

    /**
     * The {@link ReentrantReadWriteLock} used to synchronize access to the {@link #gameObjects}.
     */
    protected final ReentrantReadWriteLock rwLock;

    /**
     * The {@link Renderer} used to render the game objects.
     */
    @Getter
    protected final Renderer<I> renderer;


    /**
     * Create a new {@link ObjectManager} instance.
     *
     * @param renderer The {@link Renderer} used to render the game objects.
     */
    public ObjectManager(Renderer<I> renderer) {
        this.gameObjects = Collections.synchronizedSet(new HashSet<>());
        this.rwLock = new ReentrantReadWriteLock();
        this.renderer = renderer;

        log.info("Initialized");
    }

    /**
     * Add a new game object to this manager.
     *
     * @param gameObject the game object to add
     * @return this manager instance, for chaining
     */
    public ObjectManager<?> addGameObject(AbstractObjectBase<I> gameObject) {
        if (gameObjects.add(gameObject)) {
            gameObject.internalCreate(this);
            renderer.getCanvas().addRenderable(gameObject);

            log.info("Added new GameObject of type '{}' with ID '{}'",
                    gameObject.getClass().getSimpleName(),
                    gameObject.getId());
        }

        return this;
    }

    /**
     * Destroy a gameobject from this manager with its id.<br>
     * Does nothing if no object with the given id exists.
     *
     * @param id the id of the object you want to destroy
     * @return this manager instance, for chaining
     */
    public ObjectManager<?> destroyGameObject(long id) {
        var gameObject = getGameObjectFromID(id);
        if (gameObject != null) {
            destroyGameObject(gameObject);
        }
        return this;
    }

    /**
     * Destroy a gameobject from this manager.
     *
     * @param gameObject the game object to destroy
     * @return this manager instance, for chaining
     */
    public ObjectManager<?> destroyGameObject(AbstractObjectBase<I> gameObject) {
        if (gameObjects.remove(gameObject)) {
            gameObject.onDestroy();
            renderer.getCanvas().removeRenderable(gameObject);

            log.info("Removed GameObject of type '{}' with ID '{}'",
                    gameObject.getClass().getSimpleName(),
                    gameObject.getId());
        }

        return this;
    }

    /**
     * This method ensures that all game objects are updated.
     * This method must be called every calculation cycle, maybe from the main {@link com.snac.util.Loop}.
     *
     * @param deltaTime the time passed since the last update
     */
    public void tick(double deltaTime) {
        rwLock.readLock().lock();
        try {
            gameObjects.forEach(gO -> gO.internalUpdate(deltaTime));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get a list of all game objects that collide with a given game object.
     *
     * @param gameObject The game object to check for collisions with
     * @return List of all game objects that collide with the given game object
     */
    public List<AbstractObjectBase<I>> getCollisions(AbstractObjectBase<?> gameObject) {
        rwLock.readLock().lock();
        try {
            return gameObjects
                    .stream()
                    .filter(gO -> !gO.isDisabled())
                    .filter(gO -> gO.getHitBox().intersects(gameObject.getHitBox()))
                    .toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public List<AbstractObjectBase<I>> getCollisions(int x, int y, int width, int height, boolean includeChildren) {
        var retu = new ArrayList<AbstractObjectBase<I>>();
        rwLock.readLock().lock();
        try {
            for (var obj : gameObjects) {
                if (obj.isDisabled()) continue;

                if (obj.getHitBox().intersects(x, y, width, height)) {
                    retu.add(obj);
                }

                if (!includeChildren) continue;
                obj.getHitBox().childAction(child -> {
                    if (child.intersects(x, y, width, height)) {
                        retu.add(obj);
                    }
                });
            }
        } finally {
            rwLock.readLock().unlock();
        }
        return retu;
    }

    /**
     * Check if a game object with a given ID exists.
     *
     * @param id The ID to check
     * @return {@code true} if the UUID exists, otherwise {@code false}
     */
    public boolean containsGameObjectFromUUID(long id) {
        return getGameObjectFromID(id) != null;
    }

    /**
     * Check if a given game object exists.
     *
     * @param gameObject The game object to check
     * @return {@code true} if the game object exists, otherwise {@code false}
     */
    public boolean containsGameObject(AbstractObjectBase<?> gameObject) {
        return gameObjects.contains(gameObject);
    }

    /**
     * Get a list of all game object IDs.
     *
     * @return A list of all game object IDs
     */
    public List<Long> getGameObjectIDs() {
        rwLock.readLock().lock();
        try {
            return gameObjects
                    .stream()
                    .map(AbstractObjectBase::getId)
                    .toList();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get a stream of all game objects.
     *
     * @return A stream of all game objects
     */
    public Stream<AbstractObjectBase<I>> streamObjects() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(gameObjects).stream();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get a game object from a given UUID.
     *
     * @param id The unique object id to search for
     * @return an {@link Optional} containing the game object with the given UUID, or nothing if no such object exists
     */
    public Optional<AbstractObjectBase<I>> getGameObjectFromID(long id) {
        rwLock.readLock().lock();
        try {
            return gameObjects
                    .stream()
                    .filter(gO -> gO.getId() == id)
                    .findFirst();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Check if a given game object collides with any other game object.
     *
     * @param gameObject The game object to check for collisions with
     * @return {@code true} if the game object collides with any other game object, otherwise {@code false}
     */
    public boolean collides(AbstractObjectBase<?> gameObject) {
        return !getCollisions(gameObject).isEmpty();
    }

    /**
     * Get a list of all game objects managed by this manager.
     * This list is a copy of the internal list,
     * so modifications to the returned list will not affect the internal list.
     *
     * @return A list of all game objects managed by this manager
     */
    public List<AbstractObjectBase<?>> getGameObjects() {
        rwLock.readLock().lock();
        try {
            return List.copyOf(gameObjects);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public static synchronized long getNextID() {
        return IDs++;
    }
}
