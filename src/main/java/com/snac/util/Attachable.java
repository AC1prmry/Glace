package com.snac.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

/**
 * A base class that supports parent-child style attachment between objects.
 * <p>
 * Think of it as a lightweight "tree structure" objects can attach to each other,
 * detach, and even notify their children when something changes.
 * </p>
 *
 * <p>
 * The main idea: you can have one parent and multiple attachments (children). Each
 * {@link Attachable} knows who its parent is (if any) and keeps track of what it has attached.
 * <p>
 * If you need a concrete example, check out {@link com.snac.core.object.AbstractObjectBase}.
 * </p>
 *
 * <p>
 * You probably noticed the {@code @SuppressWarnings("unchecked")}.
 * Please use the correct generic type to avoid exceptions.
 * I couldn't figure what's the best way to make it completely type safe.
 * </p>
 *
 * @param <T> the concrete type extending this class (so subclasses can reference themselves)
 * TODO: Testssss
 */
@Slf4j
@SuppressWarnings("unchecked")
public abstract class Attachable<T extends Attachable<T>> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The parent of this {@link Attachable}. May be null if no parent exists.
     */
    @Nullable
    @Getter
    protected volatile T parent = null;

    /**
     * This set stores every directly attached object.
     */
    protected final Set<T> directAttachments = Collections.synchronizedSet(new HashSet<>());

    /**
     * Contains a flattened cache of ALL descendants, including both direct and indirect
     * attachments (children, grandchildren, etc.).
     *
     * <p><strong>Cache Updates:</strong></p>
     * This cache is automatically rebuilt via {@link #rebuildCacheRecursive()} whenever
     * an attachment is added or removed from this object or any of its descendants.
     *
     * <p><strong>Usage & Performance:</strong></p>
     * This list is specifically designed for performance-critical tasks, such as
     * collision detection. For general-purpose traversal, {@link #childAction}
     * is the recommended alternative.
     *
     * <p><strong>Why a List instead of a Set?</strong></p>
     * Although descendants are unique, a {@code List} is used to ensure
     * <b>Cache Locality</b> and O(1) random access via index. This provides
     * significantly better performance during high-frequency iterations
     * compared to a {@code Set}.
     *
     * <p><strong>Thread Safety (Snapshot Pattern):</strong></p>
     * To ensure maximum performance without locking, this list uses a volatile
     * reference-swap mechanism. To iterate safely in a multi-threaded environment,
     * you <b>must</b> capture a local snapshot of the list before use:
     * <pre>{@code
     * // CORRECT: Capture a local reference (Snapshot)
     * var descendants = getDescendants();
     * for (int i = 0; i < descendants.size(); i++) {
     * var obj = descendants.get(i);
     * // Process obj safely...
     * }
     * }</pre>
     *
     * <p><strong>Serialization:</strong></p>
     * This field is marked as {@code transient} and will not be serialized.
     * The cache is lazily rebuilt upon deserialization.
     */
    protected transient volatile List<T> allDescendantsCache = new ArrayList<>();

    /**
     * Attaches another {@link Attachable} object to this one.
     * <p>
     * If the target object is already attached elsewhere, this method will throw a {@link IllegalArgumentException}
     * </p>
     *
     * @param attachable the object to attach
     */
    public void addAttachment(T attachable) {
        if (attachable.isAttached() || attachable == this) {
            assert attachable.getParent() != null;
            var parent = attachable.getParent().getClass().getSimpleName();

            throw new IllegalArgumentException(String.format("Object %s is already attached to %s. It must be detached first.",
                    attachable.getClass().getSimpleName(),
                    parent));
        }
        directAttachments.add(attachable);
        attachable.parent = (T) this;
        rebuildCacheRecursive();
    }

    /**
     * Detaches a specific attached object.
     * <p>
     * Removing an object which isn't even attached will throw a {@link IllegalArgumentException}
     * </p>
     *
     * @param attachable the attachable to remove
     */
    public void removeAttachment(T attachable) {
        if (!directAttachments.contains(attachable)) {
            throw new IllegalArgumentException("Can't detach a not attached object.");
        }
        directAttachments.remove(attachable);
        attachable.parent = null;
        rebuildCacheRecursive();
    }

    /**
     * Detaches this object from its parent.
     * <br>Internally uses {@link #removeAttachment}, so it shouldn't throw an
     * exception unless the internal tree structure is corrupted.
     *
     * @return {@code true} if this object was successfully detached, otherwise {@code false}
     */
    public boolean detach() {
        T p = this.parent;
        if (p == null) return false;

        p.removeAttachment((T) this);
        return true;
    }

    /**
     * Name says it all, I think.
     *
     * @return {@code true} if the object is attached to another object. Otherwise {@code false}
     */
    public boolean isAttached() {
        return parent != null;
    }

    /**
     * This method recursively iterates over every descendant.
     * You can use this method to apply specific functionality to every descendant.
     * In the following example, every child (and children of child (grandchildren or something)) gets a position update when the parent is moving:
     * <pre>{@code
     *      //In this example #onPositionChange gets called when position of parent changes
     *      public void onPositionChange() {
     *          childAction(child -> {
     *              child.position.set(parent.getX(), parent.getY())
     *          });
     *      }
     * }</pre>
     *
     * @param childAction the action to perform on each child
     */
    public void childAction(Consumer<T> childAction) {
        if (directAttachments.isEmpty()) return;
        synchronized (directAttachments) {
            directAttachments.forEach(child -> {
                childAction.accept(child);
                child.childAction(childAction);
            });
        }
    }

    /**
     * TODO: No completable future
     * Returns the topmost parent (the root) in the attachment hierarchy.
     * <p>
     * If the object is attached to another object, the method walks up the chain of parents
     * until it finds the root parent (an object that has no parent).
     * </p>
     *
     * @return a {@link Optional} which contains the root parent of this object or nothing if this object has no parent
     */
    public final Optional<T> getRootParent() {
        if (getParent() == null) return Optional.empty();
        var lastParent = getParent();
        while (true) {
            var parent = lastParent.getParent();

            if (parent == null) return Optional.of(lastParent);
            else lastParent = parent;
        }
    }

    /**
     * Rebuilds the descendant cache for this object and triggers a recursive
     * update for all ancestors.
     * <p>
     * This ensures that any structural change (adding/removing) is reflected up the entire tree.
     * <p>
     * You can also take a look at {@link #allDescendantsCache} for some more information.
     */
    public void rebuildCacheRecursive() {
        var newList = new ArrayList<T>();
        collectAll(this, newList);

        this.allDescendantsCache = Collections.unmodifiableList(newList);

        var p = parent;
        if (p != null) {
            p.rebuildCacheRecursive();
        }
    }

    /**
     * Helper method to flatten the tree structure into a linear list.
     *
     * @param parent the starting point of the collection
     * @param target the list where all descendants will be stored
     */
    public static <E extends Attachable<E>> void collectAll(Attachable<E> parent, List<E> target) {
        parent.childAction(target::add);
    }

    /**
     * Returns the descendant cache.
     * Explicitly defined to provide a more descriptive name than the field itself.
     * <p>
     * Please take a look at {@link #allDescendantsCache} documentation to see how to use it and more information.
     * @return an unmodifiable list of all descendants
     */
    public List<T> getAllDescendants() {
        return allDescendantsCache;
    }
}
