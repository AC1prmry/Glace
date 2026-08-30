package com.snac.core.object;

import com.snac.graphics.Renderer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

/**
 * Class to manage {@link AbstractObjectBase objects}.
 * For a proper use of this framework you should add your {@link AbstractObjectBase objects} to a valid instance of this class.
 * This class is thread safe (at least I hope so) and does all the work related to objects for you.
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
     * This {@link Set} contains all objects managed by this manager.
     */
    protected final Set<AbstractObjectBase<I>> objects;

    /**
     * The {@link Renderer} used to render the objects.
     */
    @Getter
    protected final Renderer<I> renderer;


    /**
     * Create a new {@link ObjectManager} instance.
     *
     * @param renderer The {@link Renderer} used to render the objects.
     */
    public ObjectManager(Renderer<I> renderer) {
        this.objects = Collections.synchronizedSet(new HashSet<>());
        this.renderer = renderer;

        log.info("Initialized");
    }

    /**
     * Add a new object to this manager.
     *
     * @param object the object to add
     * @return this manager instance, for chaining
     */
    public ObjectManager<?> addObject(AbstractObjectBase<I> object) {
        if (objects.add(object)) {
            object.internalCreate(this);
            renderer.getCanvas().addRenderable(object);

            log.info("Added new Object of type '{}' with ID '{}'",
                    object.getClass().getSimpleName(),
                    object.getId());
        } else {
            throw new IllegalStateException("The Object was already added.");
        }

        return this;
    }

    /**
     * Destroys an object from this manager with its id.<br>
     * Does nothing if no object with the given id exists.
     *
     * @param id the id of the object you want to destroy
     * @return this manager instance, for chaining
     */
    public ObjectManager<?> destroyObject(long id) {
        getObjectFromID(id).ifPresent(this::destroyObject);
        return this;
    }

    /**
     * Destroys an object from this manager.
     *
     * @param object the object to destroy
     * @return this manager instance, for chaining
     */
    public ObjectManager<?> destroyObject(AbstractObjectBase<I> object) {
        if (objects.remove(object)) {
            object.onDestroy();
            renderer.getCanvas().removeRenderable(object);

            log.info("Removed Object of type '{}' with ID '{}'",
                    object.getClass().getSimpleName(),
                    object.getId());
        } else {
            throw new IllegalStateException("Non-existent Objects cannot be destroyed.");
        }

        return this;
    }

    /**
     * This method ensures that all objects are updated.
     * This method must be called every calculation cycle, maybe from the main {@link com.snac.util.Loop}.
     *
     * @param deltaTime the time passed since the last update
     */
    public void tick(double deltaTime) {
        synchronized (objects) {
            for (var object : objects) {
                object.internalUpdate(deltaTime);
            }
        }
    }

    /**
     * Get a list of all objects that collide with a given object.
     *
     * @param Object The object to check for collisions with
     * @return List of all objects that collide with the given object
     */
    public List<AbstractObjectBase<I>> getCollisions(AbstractObjectBase<?> Object) {
        synchronized (objects) {
            return objects
                    .stream()
                    .filter(gO -> !gO.isDisabled())
                    .filter(gO -> gO.getHitBox().intersects(Object.getHitBox()))
                    .toList();
        }
    }

    public List<AbstractObjectBase<I>> getCollisions(int x, int y, int width, int height, boolean includeChildren) {
        var retu = new ArrayList<AbstractObjectBase<I>>();
        synchronized (objects) {
            for (var obj : objects) {
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
        }
        return retu;
    }

    /**
     * Check if an object with a given ID exists.
     *
     * @param id The ID to check
     * @return {@code true} if the UUID exists, otherwise {@code false}
     */
    public boolean containsObjectFromUUID(long id) {
        return getObjectFromID(id).isPresent();
    }

    /**
     * Check if a given object exists.
     *
     * @param Object The object to check
     * @return {@code true} if the object exists, otherwise {@code false}
     */
    public boolean containsObject(AbstractObjectBase<?> Object) {
        return objects.contains(Object);
    }

    /**
     * Get a list of all object IDs.
     *
     * @return A list of all object IDs
     */
    public List<Long> getObjectIDs() {
        synchronized (objects) {
            return objects
                    .stream()
                    .map(AbstractObjectBase::getId)
                    .toList();
        }
    }

    /**
     * Get a object from a given UUID.
     *
     * @param id The unique object id to search for
     * @return an {@link Optional} containing the object with the given UUID, or nothing if no such object exists
     */
    public Optional<AbstractObjectBase<I>> getObjectFromID(long id) {
        synchronized (objects) {
            return objects
                    .stream()
                    .filter(gO -> gO.getId() == id)
                    .findFirst();
        }
    }

    /**
     * Check if a given object collides with any other object.
     *
     * @param Object The object to check for collisions with
     * @return {@code true} if the object collides with any other object, otherwise {@code false}
     */
    public boolean collides(AbstractObjectBase<?> Object) {
        return !getCollisions(Object).isEmpty();
    }

    /**
     * Get the number of objects managed by this manager.
     *
     * @return The number of objects managed by this manager
     */
    public int countObjects() {
        return objects.size();
    }

    /**
     * Iterate over all objects managed by this manager.
     * @param objects The consumer to call for each object
     */
    public void iterateObjects(Consumer<AbstractObjectBase<I>> objects) {
        synchronized (this.objects) {
            for (var obj : this.objects) {
                objects.accept(obj);
            }
        }
    }

    public static synchronized long getNextID() {
        return IDs++;
    }
}
